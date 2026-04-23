from fastapi import APIRouter, Request, Query
from typing import Optional
import logging

from ..services.sentiment_analyzer import SentimentAnalyzer, SentimentResult

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/sentiment", response_model=SentimentResult)
async def get_sentiment(
    request: Request,
    symbol: Optional[str] = Query(None, description="Optional ticker symbol for targeted news"),
):
    """Get market sentiment score derived from recent financial news headlines."""
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    return await sentiment_analyzer.get_market_sentiment(symbol=symbol)
