import asyncio
import httpx
from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING, ClassVar, List, Optional
from pydantic import BaseModel
import logging

from nltk.sentiment.vader import SentimentIntensityAnalyzer
import nltk

from ..config import settings

if TYPE_CHECKING:
    from ..sentiment.finbert_analyzer import FinBERTAnalyzer

logger = logging.getLogger(__name__)


class NewsItem(BaseModel):
    headline: str
    summary: Optional[str]
    source: str
    published_at: datetime
    url: Optional[str]
    sentiment_score: Optional[float] = None


class SentimentResult(BaseModel):
    overall_score: float  # -1.0 to 1.0
    label: str  # POSITIVE, NEGATIVE, NEUTRAL
    news_count: int
    headlines: List[NewsItem]


class TextSentimentResult(BaseModel):
    """Full-detail sentiment for a single arbitrary text (Ticket 23)."""

    text: str
    label: str  # POSITIVE, NEGATIVE, NEUTRAL
    compound: float  # -1.0 to 1.0
    confidence: float  # highest class probability
    positive_score: float
    negative_score: float
    neutral_score: float
    engine: str  # "finbert" or "vader" — which engine produced this result


class MarketSentimentSummary(BaseModel):
    """Aggregated sentiment across an explicit, client-provided list of texts."""

    overall_sentiment: str  # POSITIVE, NEGATIVE, NEUTRAL
    compound_score: float
    positive_ratio: float  # fraction 0.0-1.0, not a 0-100 percentage
    negative_ratio: float
    neutral_ratio: float
    headline_count: int


class SentimentAnalyzer:
    """
    Analyzes financial news sentiment using FinBERT (finance-tuned transformer),
    with automatic fallback to VADER if FinBERT is unavailable or fails.
    Provides market sentiment score for trading decisions.
    """

    FINANCIAL_LEXICON: ClassVar[dict[str, float]] = {
        "bullish": 2.0,
        "bearish": -2.0,
        "rally": 1.5,
        "crash": -2.5,
        "surge": 1.5,
        "plunge": -2.0,
        "breakout": 1.0,
        "breakdown": -1.0,
        "upgrade": 1.5,
        "downgrade": -1.5,
        "beat": 1.0,
        "miss": -1.0,
        "hawkish": -0.5,
        "dovish": 0.5,
        "recession": -2.0,
        "growth": 1.0,
        "inflation": -0.5,
        "deflation": -0.5,
    }

    def __init__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        self.vader: Optional[SentimentIntensityAnalyzer] = None
        self.finbert_model: Optional["FinBERTAnalyzer"] = None

    async def aclose(self) -> None:
        await self.client.aclose()
        self.finbert_model = None

    async def initialize(self):
        """Initialize NLP models.

        VADER is always loaded first as the guaranteed baseline. FinBERT is
        then attempted on top of it as the preferred engine (per
        settings.sentiment_primary_analyzer) — any failure to load it
        (missing ml deps, model download failure, etc.) is logged and this
        analyzer simply continues on VADER alone.
        """
        try:
            nltk.data.find("sentiment/vader_lexicon.zip")
        except LookupError:
            nltk.download("vader_lexicon", quiet=True)

        self.vader = SentimentIntensityAnalyzer()
        self.vader.lexicon.update(self.FINANCIAL_LEXICON)
        logger.info("Sentiment analyzer initialized with VADER")

        if settings.sentiment_primary_analyzer == "finbert":
            # Model download/load/warm-up is blocking, CPU/IO-bound work — offload
            # so it doesn't stall the event loop during startup.
            await asyncio.to_thread(self._load_finbert)

    def _load_finbert(self) -> None:
        """Attempt to load FinBERT as the primary analyzer."""
        try:
            from ..sentiment.finbert_analyzer import FinBERTAnalyzer

            device = None if settings.finbert_device == "auto" else settings.finbert_device
            self.finbert_model = FinBERTAnalyzer(
                device=device, max_length=settings.finbert_max_length
            )
            # Warm-up inference so the first real request isn't slowed by lazy init.
            self.finbert_model.analyze("Warm-up sentence for model initialization.")
            logger.info("FinBERT loaded and warmed up as primary sentiment engine")
        except Exception as e:
            logger.warning(f"Failed to load FinBERT ({e}); falling back to VADER only")
            self.finbert_model = None

    async def fetch_market_news(
        self,
        symbol: Optional[str] = None,
        category: str = "general",
    ) -> List[NewsItem]:
        """Fetch recent market news from Finnhub, with Alpha Vantage as fallback."""
        items = await self._fetch_from_finnhub(symbol=symbol, category=category)
        if not items and settings.alpha_vantage_api_key:
            logger.info("Finnhub returned no news; falling back to Alpha Vantage")
            items = await self._fetch_from_alpha_vantage(symbol=symbol)
        return items

    async def _fetch_from_finnhub(
        self,
        symbol: Optional[str] = None,
        category: str = "general",
    ) -> List[NewsItem]:
        """Fetch news from Finnhub (primary source)."""
        if not settings.finnhub_api_key:
            logger.warning("FINNHUB_API_KEY not configured; returning empty news")
            return []

        try:
            if symbol:
                today = datetime.now(timezone.utc).date()
                params = {
                    "symbol": symbol,
                    "from": (today - timedelta(days=7)).isoformat(),
                    "to": today.isoformat(),
                    "token": settings.finnhub_api_key,
                }
                url = "https://finnhub.io/api/v1/company-news"
            else:
                params = {"category": category, "token": settings.finnhub_api_key}
                url = "https://finnhub.io/api/v1/news"

            response = await self.client.get(url, params=params)
            response.raise_for_status()
            data = response.json()

            news_items = []
            for item in data[:20]:  # Limit to 20 most recent
                raw_ts = item.get("datetime")
                if not raw_ts:
                    continue
                news = NewsItem(
                    headline=item.get("headline", ""),
                    summary=item.get("summary"),
                    source=item.get("source", "unknown"),
                    published_at=datetime.fromtimestamp(raw_ts, tz=timezone.utc),
                    url=item.get("url"),
                )
                news_items.append(news)

            return news_items

        except (httpx.HTTPError, ValueError) as e:
            logger.error(f"Failed to fetch news from Finnhub: {e}")
            return []

    async def _fetch_from_alpha_vantage(self, symbol: Optional[str] = None) -> List[NewsItem]:
        """Fetch news from Alpha Vantage (fallback source)."""
        params: dict = {"function": "NEWS_SENTIMENT", "apikey": settings.alpha_vantage_api_key}
        if symbol:
            params["tickers"] = symbol
        else:
            params["topics"] = "financial_markets"

        try:
            response = await self.client.get("https://www.alphavantage.co/query", params=params)
            response.raise_for_status()
            data = response.json()

            if "Information" in data:
                logger.warning(f"Alpha Vantage daily API limit reached: {data['Information']}")
                return []

            if "Note" in data:
                logger.warning(f"Alpha Vantage per-minute API limit reached: {data['Note']}")
                return []

            news_items = []
            for item in data.get("feed", [])[:20]:
                try:
                    published_at = datetime.strptime(
                        item["time_published"], "%Y%m%dT%H%M%S"
                    ).replace(tzinfo=timezone.utc)
                    news = NewsItem(
                        headline=item.get("title", ""),
                        summary=item.get("summary"),
                        source=item.get("source", "alpha_vantage"),
                        published_at=published_at,
                        url=item.get("url"),
                    )
                    news_items.append(news)
                except (KeyError, TypeError, ValueError) as e:
                    logger.warning(f"Skipping malformed Alpha Vantage news item: {e}")

            return news_items

        except (httpx.HTTPError, ValueError) as e:
            logger.error(f"Failed to fetch news from Alpha Vantage: {e}")
            return []

    def _vader_scores(self, text: str) -> dict:
        """Raw VADER polarity scores, or all-zero if VADER isn't initialized."""
        if not self.vader:
            return {"pos": 0.0, "neg": 0.0, "neu": 0.0, "compound": 0.0}
        return self.vader.polarity_scores(text)

    @staticmethod
    def _vader_label(compound: float) -> str:
        if compound >= 0.05:
            return "POSITIVE"
        if compound <= -0.05:
            return "NEGATIVE"
        return "NEUTRAL"

    def _vader_result(self, text: str) -> TextSentimentResult:
        scores = self._vader_scores(text)
        return TextSentimentResult(
            text=text,
            label=self._vader_label(scores["compound"]),
            compound=scores["compound"],
            confidence=max(scores["pos"], scores["neg"], scores["neu"]),
            positive_score=scores["pos"],
            negative_score=scores["neg"],
            neutral_score=scores["neu"],
            engine="vader",
        )

    def analyze_text(self, text: str) -> TextSentimentResult:
        """
        Analyze a single text, returning full label/score/confidence detail.
        Prefers FinBERT when loaded, falling back to VADER on any failure.
        """
        if self.finbert_model:
            try:
                r = self.finbert_model.analyze(text)
                return TextSentimentResult(
                    text=text,
                    label=r.label.upper(),
                    compound=r.compound,
                    confidence=r.confidence,
                    positive_score=r.positive_score,
                    negative_score=r.negative_score,
                    neutral_score=r.neutral_score,
                    engine="finbert",
                )
            except Exception as e:
                logger.warning(f"FinBERT inference failed, falling back to VADER: {e}")

        return self._vader_result(text)

    def analyze_texts_batch(self, texts: List[str]) -> List[TextSentimentResult]:
        """
        Analyze multiple texts, returning full label/score/confidence detail
        for each. Uses FinBERT's batch inference when available (one efficient
        call), falling back to per-text VADER otherwise.
        """
        if not texts:
            return []

        if self.finbert_model:
            try:
                results = self.finbert_model.analyze_batch(
                    texts, batch_size=settings.finbert_batch_size
                )
                if len(results) != len(texts):
                    # Defense-in-depth: a mismatched count would otherwise surface
                    # later as a confusing strict=True zip crash in get_market_sentiment
                    # instead of the intended graceful VADER fallback.
                    raise ValueError(
                        f"FinBERT returned {len(results)} results for {len(texts)} texts"
                    )
                return [
                    TextSentimentResult(
                        text=r.text,
                        label=r.label.upper(),
                        compound=r.compound,
                        confidence=r.confidence,
                        positive_score=r.positive_score,
                        negative_score=r.negative_score,
                        neutral_score=r.neutral_score,
                        engine="finbert",
                    )
                    for r in results
                ]
            except Exception as e:
                logger.warning(f"FinBERT batch inference failed, falling back to VADER: {e}")

        return [self._vader_result(t) for t in texts]

    def analyze_sentiment(self, text: str) -> float:
        """
        Analyze sentiment of a single text.
        Returns score from -1.0 (negative) to 1.0 (positive).

        Thin float-returning wrapper kept for existing callers (get_market_sentiment).
        """
        return self.analyze_text(text).compound

    def aggregate_sentiment(self, texts: List[str]) -> MarketSentimentSummary:
        """Aggregate sentiment across an explicit, client-provided list of texts."""
        if not texts:
            return MarketSentimentSummary(
                overall_sentiment="NEUTRAL",
                compound_score=0.0,
                positive_ratio=0.0,
                negative_ratio=0.0,
                neutral_ratio=0.0,
                headline_count=0,
            )

        results = self.analyze_texts_batch(texts)
        total = len(results)
        positive_count = sum(1 for r in results if r.label == "POSITIVE")
        negative_count = sum(1 for r in results if r.label == "NEGATIVE")
        neutral_count = total - positive_count - negative_count
        avg_compound = sum(r.compound for r in results) / total

        if avg_compound >= 0.2:
            overall = "POSITIVE"
        elif avg_compound <= -0.2:
            overall = "NEGATIVE"
        else:
            overall = "NEUTRAL"

        return MarketSentimentSummary(
            overall_sentiment=overall,
            compound_score=round(avg_compound, 3),
            positive_ratio=round(positive_count / total, 3),
            negative_ratio=round(negative_count / total, 3),
            neutral_ratio=round(neutral_count / total, 3),
            headline_count=total,
        )

    async def get_market_sentiment(self, symbol: Optional[str] = None) -> SentimentResult:
        """Analyze overall market sentiment from recent news."""
        news_items = await self.fetch_market_news(symbol=symbol)

        if not news_items:
            return SentimentResult(
                overall_score=0.0,
                label="NEUTRAL",
                news_count=0,
                headlines=[],
            )

        texts = [
            news.headline + (" " + news.summary if news.summary else "") for news in news_items
        ]
        results = self.analyze_texts_batch(texts)

        total_score = 0.0
        for news, result in zip(news_items, results, strict=True):
            news.sentiment_score = result.compound
            total_score += result.compound

        avg_score = total_score / len(news_items)

        if avg_score >= 0.2:
            label = "POSITIVE"
        elif avg_score <= -0.2:
            label = "NEGATIVE"
        else:
            label = "NEUTRAL"

        return SentimentResult(
            overall_score=round(avg_score, 3),
            label=label,
            news_count=len(news_items),
            headlines=news_items,
        )
