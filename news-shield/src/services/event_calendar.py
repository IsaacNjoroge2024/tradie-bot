import httpx
from datetime import datetime, timedelta, timezone
from typing import ClassVar, List, Optional
from pydantic import BaseModel
from enum import Enum
import logging

from ..config import settings

logger = logging.getLogger(__name__)


class EventImpact(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class EconomicEvent(BaseModel):
    event_time: datetime
    title: str
    country: str
    currency: Optional[str]
    impact: EventImpact
    forecast: Optional[str]
    previous: Optional[str]
    actual: Optional[str]


class EventCalendarService:
    """
    Fetches economic calendar events from Finnhub (free tier).
    Identifies high-impact events that should pause trading.
    """

    HIGH_IMPACT_KEYWORDS: ClassVar[tuple[str, ...]] = (
        "nonfarm payroll",
        "nfp",
        "fomc",
        "fed rate",
        "interest rate decision",
        "cpi",
        "consumer price",
        "inflation",
        "gdp",
        "gross domestic",
        "unemployment",
        "retail sales",
        "pmi",
        "manufacturing",
        "ecb",
        "boe",
        "boj",
        "rba",  # Central bank decisions
    )

    def __init__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        self.base_url = "https://finnhub.io/api/v1"

    async def aclose(self) -> None:
        await self.client.aclose()

    async def get_upcoming_events(self, hours_ahead: int = 24) -> List[EconomicEvent]:
        """Fetch economic calendar events for the next N hours."""
        if not settings.finnhub_api_key:
            logger.warning("FINNHUB_API_KEY not configured; returning empty events")
            return []

        now_utc = datetime.now(timezone.utc)
        cutoff = now_utc + timedelta(hours=hours_ahead)
        from_date = now_utc.strftime("%Y-%m-%d")
        to_date = cutoff.strftime("%Y-%m-%d")

        try:
            response = await self.client.get(
                f"{self.base_url}/calendar/economic",
                params={"from": from_date, "to": to_date, "token": settings.finnhub_api_key},
            )
            response.raise_for_status()
            data = response.json()

            events = []
            for item in data.get("economicCalendar", []):
                try:
                    impact = self._classify_impact(item)
                    event_dt = datetime.fromisoformat(item["time"].replace("Z", "+00:00"))
                    if event_dt.tzinfo is None:
                        event_dt = event_dt.replace(tzinfo=timezone.utc)
                    event = EconomicEvent(
                        event_time=event_dt,
                        title=item.get("event", "Unknown"),
                        country=item.get("country", ""),
                        currency=item.get("currency"),
                        impact=impact,
                        forecast=item.get("estimate"),
                        previous=item.get("prev"),
                        actual=item.get("actual"),
                    )
                    events.append(event)
                except Exception as e:
                    logger.warning(f"Skipping malformed event entry: {e}")

            return [e for e in events if e.event_time <= cutoff]

        except (httpx.HTTPError, ValueError) as e:
            logger.error(f"Failed to fetch economic calendar: {e}")
            return []

    def _classify_impact(self, event: dict) -> EventImpact:
        """Classify event impact based on title keywords."""
        title = event.get("event", "").lower()

        for keyword in self.HIGH_IMPACT_KEYWORDS:
            if keyword in title:
                return EventImpact.HIGH

        impact = event.get("impact", "").lower()
        if impact == "high":
            return EventImpact.HIGH
        elif impact == "medium":
            return EventImpact.MEDIUM

        return EventImpact.LOW

    async def get_high_impact_events(self) -> List[EconomicEvent]:
        """Get only high-impact events in the next 24 hours."""
        events = await self.get_upcoming_events(hours_ahead=24)
        return [e for e in events if e.impact == EventImpact.HIGH]

    def is_event_imminent(
        self,
        event: EconomicEvent,
        pause_minutes_before: int = 30,
        pause_minutes_after: int = 60,
    ) -> bool:
        """Check if a high-impact event is within the pause window."""
        now = datetime.now(timezone.utc)
        event_time = event.event_time
        if event_time.tzinfo is None:
            event_time = event_time.replace(tzinfo=timezone.utc)
        window_start = event_time - timedelta(minutes=pause_minutes_before)
        window_end = event_time + timedelta(minutes=pause_minutes_after)
        return window_start <= now <= window_end
