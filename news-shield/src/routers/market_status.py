from fastapi import APIRouter, Request, Query
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime, timezone
import logging

from ..services.event_calendar import EventCalendarService, EconomicEvent
from ..services.sentiment_analyzer import SentimentAnalyzer, SentimentResult
from ..config import settings

logger = logging.getLogger(__name__)
router = APIRouter()


class MarketStatusResponse(BaseModel):
    safe_to_trade: bool
    risk_level: str  # "LOW", "MEDIUM", "HIGH", "EXTREME"
    reasons: List[str]
    sentiment: Optional[SentimentResult]
    upcoming_events: List[EconomicEvent]
    vix_level: Optional[float]
    timestamp: datetime


@router.get("/market-status", response_model=MarketStatusResponse)
async def get_market_status(
    request: Request,
    symbol: Optional[str] = Query(None, description="Optional symbol for specific sentiment"),
):
    """
    Main endpoint for Strategy Engine to check if trading is safe.

    Returns:
        - safe_to_trade: Boolean indicating if conditions are favorable
        - risk_level: Current market risk assessment
        - reasons: List of factors affecting the decision
    """
    event_service: EventCalendarService = request.app.state.event_service
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer

    reasons = []
    risk_level = "LOW"
    safe_to_trade = True

    # 1. Check for imminent high-impact events
    high_impact_events = await event_service.get_high_impact_events()
    imminent_events = []

    for event in high_impact_events:
        if event_service.is_event_imminent(
            event,
            pause_minutes_before=settings.high_impact_pause_minutes,
            pause_minutes_after=60,
        ):
            imminent_events.append(event)
            reasons.append(f"High-impact event imminent: {event.title} at {event.event_time}")
            safe_to_trade = False
            risk_level = "EXTREME"

    # 2. Check news sentiment
    sentiment = await sentiment_analyzer.get_market_sentiment(symbol=symbol)

    if sentiment.overall_score <= settings.sentiment_danger_threshold:
        reasons.append(f"Extremely negative sentiment: {sentiment.overall_score}")
        safe_to_trade = False
        if risk_level != "EXTREME":
            risk_level = "HIGH"
    elif sentiment.overall_score <= settings.sentiment_caution_threshold:
        reasons.append(f"Negative sentiment detected: {sentiment.overall_score}")
        if risk_level == "LOW":
            risk_level = "MEDIUM"

    # 3. TODO: Add VIX check (requires market data subscription)
    vix_level = None  # Would fetch from IBKR or data provider

    if not reasons:
        reasons.append("Market conditions appear favorable")

    return MarketStatusResponse(
        safe_to_trade=safe_to_trade,
        risk_level=risk_level,
        reasons=reasons,
        sentiment=sentiment,
        upcoming_events=high_impact_events[:5],  # Next 5 high-impact events
        vix_level=vix_level,
        timestamp=datetime.now(timezone.utc),
    )


@router.get("/quick-check")
async def quick_market_check(request: Request):
    """
    Lightweight endpoint for rapid checks.
    Returns only safe_to_trade boolean with minimal processing.
    """
    event_service: EventCalendarService = request.app.state.event_service

    high_impact_events = await event_service.get_high_impact_events()

    for event in high_impact_events:
        if event_service.is_event_imminent(
            event,
            pause_minutes_before=15,
            pause_minutes_after=30,
        ):
            return {"safe_to_trade": False, "reason": event.title}

    return {"safe_to_trade": True}
