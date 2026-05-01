import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi.testclient import TestClient

from src.main import app
from src.services.event_calendar import EventCalendarService
from src.services.sentiment_analyzer import SentimentAnalyzer, SentimentResult

client = TestClient(app)


def _setup_app_state():
    mock_event_service = MagicMock(spec=EventCalendarService)
    mock_event_service.get_high_impact_events = AsyncMock(return_value=[])

    mock_sentiment_analyzer = MagicMock(spec=SentimentAnalyzer)
    mock_sentiment_analyzer.get_market_sentiment = AsyncMock(
        return_value=SentimentResult(
            overall_score=0.1,
            label="NEUTRAL",
            news_count=0,
            headlines=[],
        )
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


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy", "service": "news-shield"}


def test_market_status_safe():
    _setup_app_state()
    response = client.get("/api/market-status")
    assert response.status_code == 200
    body = response.json()
    assert body["safe_to_trade"] is True
    assert body["risk_level"] == "LOW"
    assert "Market conditions appear favorable" in body["reasons"]


def test_quick_check_safe():
    _setup_app_state()
    response = client.get("/api/quick-check")
    assert response.status_code == 200
    assert response.json() == {"safe_to_trade": True}
