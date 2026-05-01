import pytest
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, MagicMock

from fastapi.testclient import TestClient

from src.main import app
from src.services.event_calendar import EconomicEvent, EventCalendarService, EventImpact
from src.services.sentiment_analyzer import SentimentAnalyzer, SentimentResult

client = TestClient(app)

NEUTRAL_SENTIMENT = SentimentResult(
    overall_score=0.1,
    label="NEUTRAL",
    news_count=5,
    headlines=[],
)

NEGATIVE_SENTIMENT = SentimentResult(
    overall_score=-0.6,
    label="NEGATIVE",
    news_count=5,
    headlines=[],
)

CAUTION_SENTIMENT = SentimentResult(
    overall_score=-0.35,
    label="NEGATIVE",
    news_count=5,
    headlines=[],
)


def _high_impact_event(minutes_from_now: int = 15) -> EconomicEvent:
    return EconomicEvent(
        event_time=datetime.now(timezone.utc) + timedelta(minutes=minutes_from_now),
        title="FOMC Rate Decision",
        country="US",
        currency="USD",
        impact=EventImpact.HIGH,
        forecast=None,
        previous=None,
        actual=None,
    )


def _setup_state(events=None, sentiment=None):
    mock_event_service = MagicMock(spec=EventCalendarService)
    mock_event_service.get_high_impact_events = AsyncMock(return_value=events or [])
    mock_event_service.is_event_imminent = MagicMock(return_value=bool(events))

    mock_sentiment_analyzer = MagicMock(spec=SentimentAnalyzer)
    mock_sentiment_analyzer.get_market_sentiment = AsyncMock(
        return_value=sentiment or NEUTRAL_SENTIMENT
    )

    app.state.event_service = mock_event_service
    app.state.sentiment_analyzer = mock_sentiment_analyzer


@pytest.fixture(autouse=True)
def restore_app_state():
    prev_event = getattr(app.state, "event_service", None)
    prev_sent = getattr(app.state, "sentiment_analyzer", None)
    yield
    app.state.event_service = prev_event
    app.state.sentiment_analyzer = prev_sent


class TestMarketStatusEndpoint:
    def test_safe_when_no_events_and_neutral_sentiment(self):
        _setup_state()
        response = client.get("/api/market-status")
        assert response.status_code == 200
        body = response.json()
        assert body["safe_to_trade"] is True
        assert body["risk_level"] == "LOW"
        assert len(body["reasons"]) > 0
        assert "timestamp" in body

    def test_unsafe_when_imminent_high_impact_event(self):
        event = _high_impact_event(minutes_from_now=15)
        _setup_state(events=[event])
        response = client.get("/api/market-status")
        assert response.status_code == 200
        body = response.json()
        assert body["safe_to_trade"] is False
        assert body["risk_level"] == "EXTREME"
        assert any("FOMC" in r for r in body["reasons"])

    def test_unsafe_on_danger_sentiment(self):
        _setup_state(sentiment=NEGATIVE_SENTIMENT)
        app.state.event_service.is_event_imminent = MagicMock(return_value=False)
        response = client.get("/api/market-status")
        assert response.status_code == 200
        body = response.json()
        assert body["safe_to_trade"] is False
        assert body["risk_level"] == "HIGH"
        assert any("negative sentiment" in r.lower() for r in body["reasons"])

    def test_medium_risk_on_caution_sentiment(self):
        _setup_state(sentiment=CAUTION_SENTIMENT)
        app.state.event_service.is_event_imminent = MagicMock(return_value=False)
        response = client.get("/api/market-status")
        assert response.status_code == 200
        body = response.json()
        assert body["risk_level"] == "MEDIUM"
        assert body["safe_to_trade"] is True

    def test_accepts_optional_symbol_param(self):
        _setup_state()
        response = client.get("/api/market-status?symbol=AAPL")
        assert response.status_code == 200

    def test_response_includes_upcoming_events(self):
        event = _high_impact_event(minutes_from_now=15)
        _setup_state(events=[event])
        response = client.get("/api/market-status")
        body = response.json()
        assert "upcoming_events" in body
        assert len(body["upcoming_events"]) == 1


class TestQuickCheckEndpoint:
    def test_safe_when_no_imminent_events(self):
        _setup_state()
        app.state.event_service.is_event_imminent = MagicMock(return_value=False)
        response = client.get("/api/quick-check")
        assert response.status_code == 200
        assert response.json() == {"safe_to_trade": True}

    def test_unsafe_when_imminent_event(self):
        event = _high_impact_event(minutes_from_now=10)
        _setup_state(events=[event])
        app.state.event_service.is_event_imminent = MagicMock(return_value=True)
        response = client.get("/api/quick-check")
        assert response.status_code == 200
        body = response.json()
        assert body["safe_to_trade"] is False
        assert "reason" in body
        assert body["reason"] == "FOMC Rate Decision"


class TestEventsEndpoint:
    def test_get_upcoming_events(self):
        _setup_state()
        app.state.event_service.get_upcoming_events = AsyncMock(return_value=[])
        response = client.get("/api/events")
        assert response.status_code == 200
        assert response.json() == []

    def test_get_high_impact_events_returns_list(self):
        event = _high_impact_event(minutes_from_now=60)
        _setup_state(events=[event])
        response = client.get("/api/events/high-impact")
        assert response.status_code == 200
        body = response.json()
        assert isinstance(body, list)
        assert len(body) == 1
        assert body[0]["title"] == "FOMC Rate Decision"
        assert body[0]["impact"] == EventImpact.HIGH.value


class TestSentimentEndpoint:
    def test_get_sentiment(self):
        _setup_state()
        response = client.get("/api/sentiment")
        assert response.status_code == 200
        body = response.json()
        assert "overall_score" in body
        assert "label" in body
        assert "news_count" in body

    def test_get_sentiment_with_symbol(self):
        _setup_state()
        response = client.get("/api/sentiment?symbol=TSLA")
        assert response.status_code == 200
