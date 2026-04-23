from fastapi import APIRouter, Request, Query
from typing import List
import logging

from ..services.event_calendar import EconomicEvent, EventCalendarService

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/events", response_model=List[EconomicEvent])
async def get_upcoming_events(
    request: Request,
    hours_ahead: int = Query(24, ge=1, le=168, description="Hours ahead to look for events"),
):
    """List upcoming economic calendar events."""
    event_service: EventCalendarService = request.app.state.event_service
    return await event_service.get_upcoming_events(hours_ahead=hours_ahead)


@router.get("/events/high-impact", response_model=List[EconomicEvent])
async def get_high_impact_events(request: Request):
    """List high-impact economic events in the next 24 hours."""
    event_service: EventCalendarService = request.app.state.event_service
    return await event_service.get_high_impact_events()
