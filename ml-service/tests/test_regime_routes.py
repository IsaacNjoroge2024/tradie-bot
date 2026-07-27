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


def test_detect_regime_rejects_mismatched_array_lengths():
    """pd.DataFrame({...}) on unequal-length lists raises an uncaught ValueError
    (500) rather than a clean validation error — must be rejected before that."""
    payload = _price_data_payload()
    payload["close"] = payload["close"][:-1]  # one shorter than the rest

    response = client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": payload},
    )

    assert response.status_code == 422


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


def test_multi_timeframe_returns_should_trade_decision():
    payload = _price_data_payload()
    response = client.post(
        "/api/regime/multi-timeframe",
        json={
            "symbol": "AAPL",
            "price_data_1h": payload,
            "price_data_4h": payload,
            "price_data_1d": payload,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["symbol"] == "AAPL"
    assert set(body["regimes"].keys()) == {"1H", "4H", "1D"}
    for tf_result in body["regimes"].values():
        assert tf_result["regime"] in {"trending_up", "trending_down", "ranging", "volatile"}
        assert 0 <= tf_result["probability"] <= 1
    assert isinstance(body["should_trade"], bool)
    assert body["reason"]
    assert body["dominant_regime"] in {"trending_up", "trending_down", "ranging", "volatile"}
    assert 0 <= body["alignment"] <= 1
    assert 0 <= body["avg_confidence"] <= 1


def test_multi_timeframe_rejects_insufficient_bars_on_any_timeframe():
    payload = _price_data_payload()
    short_payload = _price_data_payload(n=10)

    response = client.post(
        "/api/regime/multi-timeframe",
        json={
            "symbol": "AAPL",
            "price_data_1h": payload,
            "price_data_4h": short_payload,
            "price_data_1d": payload,
        },
    )

    assert response.status_code == 400
    assert "4H" in response.json()["detail"]


def test_multi_timeframe_uses_the_same_cached_detectors_as_single_timeframe_detect():
    """get_multi_timeframe_regime fits through the same per-key cache as /detect,
    so a prior /detect call for a timeframe must be reused here too."""
    payload = _price_data_payload()

    client.post(
        "/api/regime/detect",
        json={"symbol": "AAPL", "timeframe": "1H", "price_data": payload},
    )
    detector_from_detect = app.state.regime_service.detectors["AAPL_1H"]

    client.post(
        "/api/regime/multi-timeframe",
        json={
            "symbol": "AAPL",
            "price_data_1h": payload,
            "price_data_4h": payload,
            "price_data_1d": payload,
        },
    )

    assert app.state.regime_service.detectors["AAPL_1H"] is detector_from_detect
