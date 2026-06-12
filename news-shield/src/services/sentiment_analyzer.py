import httpx
from datetime import datetime, timedelta, timezone
from typing import ClassVar, List, Optional
from pydantic import BaseModel
import logging

from nltk.sentiment.vader import SentimentIntensityAnalyzer
import nltk

from ..config import settings

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


class SentimentAnalyzer:
    """
    Analyzes financial news sentiment using VADER.
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
        self.finbert_model = None  # Optional: for higher accuracy

    async def aclose(self) -> None:
        await self.client.aclose()

    async def initialize(self):
        """Initialize NLP models."""
        try:
            nltk.data.find("sentiment/vader_lexicon.zip")
        except LookupError:
            nltk.download("vader_lexicon", quiet=True)

        self.vader = SentimentIntensityAnalyzer()
        self.vader.lexicon.update(self.FINANCIAL_LEXICON)
        logger.info("Sentiment analyzer initialized with VADER")

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
            response = await self.client.get(
                "https://www.alphavantage.co/query", params=params
            )
            response.raise_for_status()
            data = response.json()

            if "Information" in data:
                logger.warning(f"Alpha Vantage API limit reached: {data['Information']}")
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

    def analyze_sentiment(self, text: str) -> float:
        """
        Analyze sentiment of a single text.
        Returns score from -1.0 (negative) to 1.0 (positive).
        """
        if not self.vader:
            return 0.0

        scores = self.vader.polarity_scores(text)
        return scores["compound"]

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

        total_score = 0.0
        for news in news_items:
            text = news.headline
            if news.summary:
                text += " " + news.summary

            score = self.analyze_sentiment(text)
            news.sentiment_score = score
            total_score += score

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
