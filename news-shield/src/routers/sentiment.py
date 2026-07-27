import asyncio
from fastapi import APIRouter, Request, Query
from pydantic import BaseModel, Field
from typing import List, Optional
import logging

from ..services.sentiment_analyzer import (
    MarketSentimentSummary,
    SentimentAnalyzer,
    SentimentResult,
    TextSentimentResult,
)

logger = logging.getLogger(__name__)
router = APIRouter()


class TextSentimentRequest(BaseModel):
    text: str = Field(min_length=1, max_length=5000)


class BatchSentimentRequest(BaseModel):
    # Cap well above the "100+ headlines" efficiency target (already verified live
    # at 120 headlines) so legitimate batches aren't rejected, while still bounding
    # worst-case request cost.
    texts: List[str] = Field(min_length=1, max_length=500)


@router.get("/sentiment", response_model=SentimentResult)
async def get_sentiment(
    request: Request,
    symbol: Optional[str] = Query(None, description="Optional ticker symbol for targeted news"),
):
    """Get market sentiment score derived from recent financial news headlines."""
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    return await sentiment_analyzer.get_market_sentiment(symbol=symbol)


@router.post("/sentiment/analyze", response_model=TextSentimentResult)
async def analyze_single_text(request: Request, body: TextSentimentRequest):
    """Analyze sentiment of a single piece of text (headline, tweet, etc.)."""
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    # CPU/GPU-bound inference; offload so it doesn't block the event loop.
    return await asyncio.to_thread(sentiment_analyzer.analyze_text, body.text)


@router.post("/sentiment/analyze-batch", response_model=List[TextSentimentResult])
async def analyze_batch(request: Request, body: BatchSentimentRequest):
    """Analyze sentiment of multiple texts in one batched call."""
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    return await asyncio.to_thread(sentiment_analyzer.analyze_texts_batch, body.texts)


@router.post("/sentiment/market-sentiment", response_model=MarketSentimentSummary)
async def market_sentiment_from_headlines(request: Request, body: BatchSentimentRequest):
    """Aggregate sentiment across a client-provided list of headlines."""
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    return await asyncio.to_thread(sentiment_analyzer.aggregate_sentiment, body.texts)
