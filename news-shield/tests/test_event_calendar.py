import httpx
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.services.event_calendar import EconomicEvent, EventCalendarService, EventImpact

FUTURE_EVENT_TIME = datetime.now(timezone.utc) + timedelta(minutes=15)
PAST_EVENT_TIME = datetime.now(timezone.utc) - timedelta(hours=2)


def _make_event(
    impact: EventImpact = EventImpact.HIGH,
    event_time: datetime | None = None,
    title: str = "FOMC Rate Decision",
) -> EconomicEvent:
    return EconomicEvent(
        event_time=event_time or FUTURE_EVENT_TIME,
        title=title,
        country="US",
        currency="USD",
        impact=impact,
        forecast=None,
        previous=None,
        actual=None,
    )


class TestEventCalendarServiceClassification:
    def setup_method(self):
        self.service = EventCalendarService()

    def test_classifies_high_impact_by_keyword(self):
        item = {"event": "Nonfarm Payroll Report", "impact": ""}
        assert self.service._classify_impact(item) == EventImpact.HIGH

    def test_classifies_high_impact_by_fomc(self):
        item = {"event": "FOMC Meeting Minutes", "impact": ""}
        assert self.service._classify_impact(item) == EventImpact.HIGH

    def test_classifies_high_impact_by_cpi(self):
        item = {"event": "US CPI Data Release", "impact": ""}
        assert self.service._classify_impact(item) == EventImpact.HIGH

    def test_classifies_high_impact_by_finnhub_flag(self):
        item = {"event": "Some Unknown Event", "impact": "high"}
        assert self.service._classify_impact(item) == EventImpact.HIGH

    def test_classifies_medium_impact(self):
        item = {"event": "Some Event", "impact": "medium"}
        assert self.service._classify_impact(item) == EventImpact.MEDIUM

    def test_classifies_low_impact_by_default(self):
        item = {"event": "Local Holiday", "impact": ""}
        assert self.service._classify_impact(item) == EventImpact.LOW


class TestIsEventImminent:
    def setup_method(self):
        self.service = EventCalendarService()

    def test_event_in_window_is_imminent(self):
        event = _make_event(event_time=datetime.now(timezone.utc) + timedelta(minutes=15))
        assert self.service.is_event_imminent(event, pause_minutes_before=30) is True

    def test_event_just_passed_is_imminent(self):
        event = _make_event(event_time=datetime.now(timezone.utc) - timedelta(minutes=30))
        assert self.service.is_event_imminent(event, pause_minutes_after=60) is True

    def test_event_far_in_future_is_not_imminent(self):
        event = _make_event(event_time=datetime.now(timezone.utc) + timedelta(hours=3))
        assert self.service.is_event_imminent(event, pause_minutes_before=30) is False

    def test_event_long_past_is_not_imminent(self):
        event = _make_event(event_time=datetime.now(timezone.utc) - timedelta(hours=3))
        assert self.service.is_event_imminent(event, pause_minutes_after=60) is False

    def test_naive_event_time_handled(self):
        # Build a naive datetime equivalent to UTC+10min by stripping tzinfo.
        # Service normalises naive datetimes to UTC, so the result is deterministic.
        utc_plus_10 = datetime.now(timezone.utc) + timedelta(minutes=10)
        naive_time = utc_plus_10.replace(tzinfo=None)
        event = EconomicEvent(
            event_time=naive_time,
            title="Test",
            country="US",
            currency="USD",
            impact=EventImpact.HIGH,
            forecast=None,
            previous=None,
            actual=None,
        )
        # 10 min ahead (treated as UTC) is within the 30-min pre-event window
        assert self.service.is_event_imminent(event, pause_minutes_before=30) is True


class TestGetUpcomingEvents:
    @pytest.fixture(autouse=True)
    def set_api_key(self):
        with patch("src.services.event_calendar.settings") as mock_settings:
            mock_settings.finnhub_api_key = "test-key"
            yield mock_settings

    @pytest.fixture
    async def service(self):
        s = EventCalendarService()
        yield s
        await s.aclose()

    @pytest.mark.asyncio
    async def test_returns_empty_list_on_api_error(self, service):
        with patch.object(service.client, "get", side_effect=httpx.HTTPError("Network error")):
            result = await service.get_upcoming_events()
        assert result == []

    @pytest.mark.asyncio
    async def test_parses_events_correctly(self, service):
        event_time = (datetime.now(timezone.utc) + timedelta(hours=1)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "economicCalendar": [
                {
                    "time": event_time,
                    "event": "FOMC Rate Decision",
                    "country": "US",
                    "currency": "USD",
                    "impact": "high",
                    "estimate": "5.25%",
                    "prev": "5.00%",
                    "actual": None,
                }
            ]
        }

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.get_upcoming_events(hours_ahead=24)

        assert len(result) == 1
        assert result[0].title == "FOMC Rate Decision"
        assert result[0].impact == EventImpact.HIGH
        assert result[0].country == "US"

    @pytest.mark.asyncio
    async def test_filters_events_beyond_cutoff(self, service):
        far_future = (datetime.now(timezone.utc) + timedelta(hours=48)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "economicCalendar": [
                {
                    "time": far_future,
                    "event": "GDP Report",
                    "country": "US",
                    "currency": "USD",
                    "impact": "high",
                    "estimate": None,
                    "prev": None,
                    "actual": None,
                }
            ]
        }

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.get_upcoming_events(hours_ahead=24)

        assert result == []

    @pytest.mark.asyncio
    async def test_get_high_impact_events_filters_low(self, service):
        event_time = (datetime.now(timezone.utc) + timedelta(hours=1)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        mock_response = MagicMock()
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "economicCalendar": [
                {
                    "time": event_time,
                    "event": "Local Holiday",
                    "country": "US",
                    "currency": "USD",
                    "impact": "low",
                    "estimate": None,
                    "prev": None,
                    "actual": None,
                }
            ]
        }

        with patch.object(service.client, "get", new=AsyncMock(return_value=mock_response)):
            result = await service.get_high_impact_events()

        assert result == []
