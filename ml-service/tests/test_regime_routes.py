import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

from src.main import app
from src.regime.regime_service import RegimeService

client = TestClient(app)


def _price_data_payload(n: int = 300) -> dict:
    np.random.seed(42)
    returns = np.random.normal(0.001, 0.01, n)
    close = 100 * np.cumprod(1 + returns)
    dates = pd.date_range("2024-01-01", periods=n, freq="D", tz="UTC")

    return {
        "timestamp": [d.isoformat() for d in dates],
        "open": (close - 0.1).tolist(),
        "high": (close * 1.005).tolist(),
        "low": (close * 0.995).tolist(),
        "close": close.tolist(),
        "volume": np.random.uniform(1000000, 2000000, n).tolist(),
    }


@pytest.fixture(autouse=True)
def fresh_regime_service():
    app.state.regime_service = RegimeService()
    yield
    app.state.regime_service = RegimeService()


def test_detect_regime_returns_full_payload():
    response = client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": _price_data_payload()},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["symbol"] == "AAPL"
    assert body["timeframe"] == "1H"
    assert body["regime"] in {"trending_up", "trending_down", "ranging", "volatile"}
    assert 0 <= body["probability"] <= 1
    assert set(body["all_probabilities"].keys()) == {
        "trending_up",
        "trending_down",
        "ranging",
        "volatile",
    }
    rec = body["recommendation"]
    assert rec["position_size_multiplier"] > 0
    assert isinstance(rec["preferred_strategies"], list)
    assert isinstance(rec["avoid_strategies"], list)
    assert rec["stop_loss_multiplier"] > 0
    assert rec["take_profit_multiplier"] > 0
    assert rec["max_positions"] > 0
    assert rec["notes"]


def test_detect_regime_rejects_insufficient_bars():
    payload = _price_data_payload(n=10)
    response = client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": payload},
    )

    assert response.status_code == 400


def test_detect_regime_reuses_cached_detector_across_requests():
    """Regression test: the route must not build a fresh, empty RegimeService
    per request, or the 4h retrain-interval caching design is defeated and
    every request pays a full HMM refit."""
    payload = _price_data_payload()

    client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": payload},
    )
    detector_after_first_call = app.state.regime_service.detectors["AAPL_1H"]

    client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": payload},
    )

    assert app.state.regime_service.detectors["AAPL_1H"] is detector_after_first_call
