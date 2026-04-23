import httpx
from datetime import datetime
from typing import List, Optional
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

    FINANCIAL_LEXICON = {
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
        """Fetch recent market news from Finnhub."""
        try:
            params = {"category": category, "token": settings.finnhub_api_key}

            response = await self.client.get(
                "https://finnhub.io/api/v1/news",
                params=params,
            )
            response.raise_for_status()
            data = response.json()

            news_items = []
            for item in data[:20]:  # Limit to 20 most recent
                news = NewsItem(
                    headline=item.get("headline", ""),
                    summary=item.get("summary"),
                    source=item.get("source", "unknown"),
                    published_at=datetime.fromtimestamp(item.get("datetime", 0)),
                    url=item.get("url"),
                )
                news_items.append(news)

            return news_items

        except Exception as e:
            logger.error(f"Failed to fetch news: {e}")
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
