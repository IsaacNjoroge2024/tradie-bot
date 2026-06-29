import pandas as pd
import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, MagicMock

from src.main import app
from src.data.loader import HistoricalDataLoader
from src.data.timescale import TimescaleDBPool


def _make_sample_data() -> pd.DataFrame:
    """Generate 100 bars of synthetic OHLCV data."""
    import numpy as np

    np.random.seed(42)
    dates = pd.date_range("2023-01-01", periods=100, freq="D", tz="UTC")
    close = 100.0 + np.cumsum(np.random.randn(100) * 0.5)
    df = pd.DataFrame(
        {
            "open": close - 0.2,
            "high": close + 0.5,
            "low": close - 0.5,
            "close": close,
            "volume": np.random.randint(1000, 5000, size=100).astype(float),
        },
        index=dates,
    )
    return df


def _setup_app_state(data: pd.DataFrame | None = None):
    mock_loader = MagicMock(spec=HistoricalDataLoader)
    mock_loader.load = AsyncMock(return_value=data if data is not None else _make_sample_data())
    app.state.data_loader = mock_loader

    mock_db = MagicMock(spec=TimescaleDBPool)
    mock_db.is_connected = True
    app.state.db_pool = mock_db


@pytest.fixture(autouse=True)
def restore_app_state():
    prev_loader = getattr(app.state, "data_loader", None)
    prev_db = getattr(app.state, "db_pool", None)
    yield
    app.state.data_loader = prev_loader
    app.state.db_pool = prev_db


client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy", "service": "backtesting-service"}


def test_run_backtest_fvg():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
        "strategy_params": {"min_gap_atr": 0.0},
        "initial_cash": 100000.0,
        "commission": 0.001,
        "engine": "pandas",
    }
    response = client.post("/api/backtest/run", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["strategy"] == "FVG_Strategy"
    assert body["symbol"] == "AAPL"
    assert "metrics" in body
    assert "equity_curve" in body
    assert len(body["equity_curve"]) == 100


def test_run_backtest_confluence():
    _setup_app_state()
    payload = {
        "symbol": "BTCUSD",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "Confluence_Strategy",
        "strategy_params": {},
        "initial_cash": 50000.0,
        "commission": 0.001,
        "engine": "pandas",
    }
    response = client.post("/api/backtest/run", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["strategy"] == "Confluence_Strategy"
    assert "metrics" in body


def test_run_backtest_unknown_strategy():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "NoSuchStrategy",
    }
    response = client.post("/api/backtest/run", json=payload)
    assert response.status_code == 400


def test_run_backtest_no_data():
    _setup_app_state(data=pd.DataFrame(columns=["open", "high", "low", "close", "volume"]))
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
    }
    response = client.post("/api/backtest/run", json=payload)
    assert response.status_code == 404


def test_optimize_strategy():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
        "param_grid": {"min_gap_atr": [0.0, 0.3], "atr_period": [10, 14]},
        "initial_cash": 100000.0,
        "commission": 0.001,
        "optimize_metric": "sharpe_ratio",
    }
    response = client.post("/api/backtest/optimize", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert "best_params" in body
    assert "best_metric_value" in body
    assert len(body["all_results"]) == 4  # 2x2 grid


def test_walk_forward():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
        "param_grid": {"min_gap_atr": [0.0, 0.3]},
        "in_sample_pct": 0.7,
        "num_periods": 3,
        "initial_cash": 100000.0,
        "commission": 0.001,
    }
    response = client.post("/api/backtest/walk-forward", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert "combined_return" in body
    assert "consistency" in body
    assert "period_results" in body


def test_compare_strategies():
    _setup_app_state()
    response = client.get(
        "/api/backtest/compare",
        params={
            "strategies": ["FVG_Strategy", "Confluence_Strategy"],
            "symbol": "AAPL",
            "timeframe": "1D",
            "start_date": "2023-01-01",
            "end_date": "2023-12-31",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert "best_strategy" in body
    assert len(body["strategies"]) == 2


def test_generate_report():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
        "strategy_params": {"min_gap_atr": 0.0},
        "initial_cash": 100000.0,
        "commission": 0.001,
        "engine": "pandas",
    }
    response = client.post("/api/backtest/report", json=payload)
    assert response.status_code == 200
    assert "text/html" in response.headers["content-type"]
    assert "FVG_Strategy" in response.text
    assert "Performance Metrics" in response.text


def test_monte_carlo():
    _setup_app_state()
    payload = {
        "symbol": "AAPL",
        "timeframe": "1D",
        "start_date": "2023-01-01",
        "end_date": "2023-12-31",
        "strategy": "FVG_Strategy",
        "strategy_params": {"min_gap_atr": 0.0},
        "initial_cash": 100000.0,
        "commission": 0.001,
        "num_simulations": 200,
    }
    response = client.post("/api/backtest/monte-carlo", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert "median_return" in body
    assert "drawdown_95th" in body
    assert "risk_of_ruin" in body
    assert 0.0 <= body["risk_of_ruin"] <= 1.0
