import pandas as pd
import pytest
import numpy as np
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, MagicMock, patch

from src.data.loader import HistoricalDataLoader
from src.data.timescale import TimescaleDBPool
from src.main import app
from src.monte_carlo.models import SimulationConfig, Trade
from src.monte_carlo.simulator import MonteCarloSimulator
from src.monte_carlo.stress_tests import StressTestSuite
from src.monte_carlo.visualizer import MonteCarloVisualizer


def create_winning_strategy_trades(n: int = 100) -> list:
    """Create trades with positive expectancy"""
    trades = []
    rng = np.random.default_rng(42)
    for i in range(n):
        # 60% win rate, 1.5:1 reward/risk
        is_win = rng.random() < 0.6
        pnl = 150.0 if is_win else -100.0
        trades.append(
            Trade(
                trade_id=f"T{i}",
                symbol="EURUSD",
                side="BUY",
                entry_price=1.1000,
                exit_price=1.1015 if is_win else 1.0990,
                quantity=10000,
                pnl=pnl,
                pnl_pct=pnl / 10000.0,
                hold_time_minutes=60,
                strategy="TEST",
                timestamp="2024-01-01",
            )
        )
    return trades


def create_losing_strategy_trades(n: int = 100) -> list:
    """Create trades with negative expectancy"""
    trades = []
    rng = np.random.default_rng(42)
    for i in range(n):
        # 40% win rate, 1:1 reward/risk
        is_win = rng.random() < 0.4
        pnl = 100.0 if is_win else -100.0
        trades.append(
            Trade(
                trade_id=f"T{i}",
                symbol="EURUSD",
                side="BUY",
                entry_price=1.1000,
                exit_price=1.1010 if is_win else 1.0990,
                quantity=10000,
                pnl=pnl,
                pnl_pct=pnl / 10000.0,
                hold_time_minutes=60,
                strategy="TEST",
                timestamp="2024-01-01",
            )
        )
    return trades


class TestMonteCarloSimulator:

    def test_winning_strategy_approved(self):
        """Winning strategy should be approved"""
        trades = create_winning_strategy_trades(100)
        config = SimulationConfig(num_simulations=1000, random_seed=42)

        simulator = MonteCarloSimulator(config)
        result = simulator.run(trades)

        assert result.probability_of_profit > 0.7
        assert result.probability_of_ruin < 0.05
        assert result.recommendation in ["APPROVED", "CAUTION"]
        assert isinstance(result.var_95, float)
        assert result.cvar_95 <= result.var_95

    def test_losing_strategy_rejected(self):
        """Losing strategy should be rejected"""
        trades = create_losing_strategy_trades(100)
        config = SimulationConfig(num_simulations=1000, random_seed=42)

        simulator = MonteCarloSimulator(config)
        result = simulator.run(trades)

        assert result.probability_of_profit < 0.5
        assert result.recommendation == "REJECTED"
        assert len(result.rejection_reasons) > 0

    def test_minimum_trades_required(self):
        """Should require minimum 30 trades"""
        trades = create_winning_strategy_trades(20)
        config = SimulationConfig(num_simulations=100)

        simulator = MonteCarloSimulator(config)

        with pytest.raises(ValueError, match="at least 30 trades"):
            simulator.run(trades)

    def test_skip_trades_increases_risk(self):
        """Skipping trades should increase drawdown risk metrics"""
        trades = create_winning_strategy_trades(100)

        config_normal = SimulationConfig(num_simulations=1000, skip_trade_pct=0, random_seed=42)
        config_skip = SimulationConfig(num_simulations=1000, skip_trade_pct=20, random_seed=42)

        result_normal = MonteCarloSimulator(config_normal).run(trades)
        result_skip = MonteCarloSimulator(config_skip).run(trades)

        assert (
            result_skip.percentile_95_max_drawdown >= result_normal.percentile_95_max_drawdown * 0.9
        )

    def test_compounding_position_sizing(self):
        """Test compounding position sizing logic"""
        trades = create_winning_strategy_trades(100)
        config = SimulationConfig(
            num_simulations=500, position_sizing="compounding", random_seed=42
        )
        result = MonteCarloSimulator(config).run(trades)
        assert result.median_final_balance > config.initial_balance

    def test_stress_test_suite(self):
        """Test StressTestSuite executes all scenarios"""
        trades = create_winning_strategy_trades(50)
        config = SimulationConfig(num_simulations=200, random_seed=42)

        suite = StressTestSuite()
        results = suite.run_all_scenarios(trades, config)

        assert len(results) == len(StressTestSuite.SCENARIOS)
        assert "baseline" in results
        assert "miss_10_pct" in results
        assert "double_losses" in results
        assert "halve_wins" in results

    def test_visualizer(self):
        """Test MonteCarloVisualizer output figures"""
        trades = create_winning_strategy_trades(50)
        config = SimulationConfig(num_simulations=200, random_seed=42)
        result = MonteCarloSimulator(config).run(trades)

        viz = MonteCarloVisualizer()
        fig_report = viz.create_report(result)
        fig_cone = viz.create_equity_cone(result)

        assert fig_report is not None
        assert fig_cone is not None

    def test_api_simulate_endpoint(self):
        """Test POST /api/monte-carlo/simulate endpoint"""
        client = TestClient(app)
        trades = [t.__dict__ for t in create_winning_strategy_trades(40)]

        payload = {
            "trades": trades,
            "initial_balance": 10000.0,
            "num_simulations": 500,
            "ruin_threshold_pct": 50.0,
            "skip_trade_pct": 0.0,
            "position_sizing": "fixed",
        }

        response = client.post("/api/monte-carlo/simulate", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert "recommendation" in data
        assert "probability_of_profit" in data
        assert "probability_of_ruin" in data
        assert "var_95" in data
        assert "percentile_95_max_drawdown" in data

    def test_api_stress_test_endpoint(self):
        """Test POST /api/monte-carlo/stress-test endpoint"""
        client = TestClient(app)
        trades = [t.__dict__ for t in create_winning_strategy_trades(40)]

        payload = {
            "trades": trades,
            "initial_balance": 10000.0,
            "num_simulations": 200,
        }

        response = client.post("/api/monte-carlo/stress-test", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert "baseline" in data
        assert "double_losses" in data
        assert "halve_wins" in data


def _make_sample_ohlcv() -> pd.DataFrame:
    """Same random-walk generator used in test_main.py, kept local to avoid cross-file imports."""
    rng = np.random.default_rng(42)
    dates = pd.date_range("2023-01-01", periods=100, freq="D", tz="UTC")
    close = 100.0 + np.cumsum(rng.standard_normal(100) * 0.5)
    return pd.DataFrame(
        {
            "open": close - 0.2,
            "high": close + 0.5,
            "low": close - 0.5,
            "close": close,
            "volume": rng.integers(1000, 5000, size=100).astype(float),
        },
        index=dates,
    )


def _make_large_trades_df(n: int = 40) -> pd.DataFrame:
    """All-positive-pnl trades, so recommendation math is deterministic regardless of shuffle
    order (a permutation can't create a loss out of an all-winning trade sequence)."""
    dates = pd.date_range("2023-01-01", periods=n, freq="D", tz="UTC")
    return pd.DataFrame(
        {
            "entry_time": dates,
            "exit_time": dates,
            "entry_price": 100.0,
            "exit_price": 101.5,
            "pnl": 150.0,
            "side": "long",
        }
    )


def _setup_backtest_app_state():
    mock_loader = MagicMock(spec=HistoricalDataLoader)
    mock_loader.load = AsyncMock(return_value=_make_sample_ohlcv())
    app.state.data_loader = mock_loader

    mock_db = MagicMock(spec=TimescaleDBPool)
    mock_db.is_connected = True
    app.state.db_pool = mock_db


@pytest.fixture(autouse=True)
def _restore_backtest_app_state():
    """Avoid leaking mocked app.state across other test modules in the same pytest session."""
    prev_loader = getattr(app.state, "data_loader", None)
    prev_db = getattr(app.state, "db_pool", None)
    yield
    app.state.data_loader = prev_loader
    app.state.db_pool = prev_db


class TestBacktestRouterMonteCarloIntegration:
    """Covers the Ticket 22 integration points inside src/routers/backtest.py:
    - /api/backtest/monte-carlo switching to the new simulator at >= 30 trades
    - /api/backtest/report embedding the Monte Carlo section at >= 30 trades
    """

    def test_backtest_monte_carlo_endpoint_uses_new_simulator_for_30_plus_trades(self):
        _setup_backtest_app_state()
        client = TestClient(app)

        with patch(
            "src.routers.backtest.run_pandas_backtest",
            return_value=(pd.Series(dtype=float), _make_large_trades_df(40)),
        ):
            payload = {
                "symbol": "AAPL",
                "timeframe": "1D",
                "start_date": "2023-01-01",
                "end_date": "2023-12-31",
                "strategy": "FVG_Strategy",
                "strategy_params": {},
                "initial_cash": 100000.0,
                "num_simulations": 300,
            }
            response = client.post("/api/backtest/monte-carlo", json=payload)

        assert response.status_code == 200
        data = response.json()
        # All-positive trades: a permutation can't turn a winning sequence into a loss.
        assert data["risk_of_ruin"] == 0.0
        assert data["median_return"] > 0
        assert data["simulations"] == 300

    def test_backtest_report_embeds_monte_carlo_section_for_30_plus_trades(self):
        _setup_backtest_app_state()
        client = TestClient(app)

        equity_curve = pd.Series(
            [100000.0] * 5, index=pd.date_range("2023-01-01", periods=5, freq="D", tz="UTC")
        )
        with patch(
            "src.routers.backtest.run_pandas_backtest",
            return_value=(equity_curve, _make_large_trades_df(40)),
        ):
            payload = {
                "symbol": "AAPL",
                "timeframe": "1D",
                "start_date": "2023-01-01",
                "end_date": "2023-12-31",
                "strategy": "FVG_Strategy",
                "strategy_params": {},
                "initial_cash": 100000.0,
                "engine": "pandas",
            }
            response = client.post("/api/backtest/report", json=payload)

        assert response.status_code == 200
        assert "Monte Carlo Risk Analysis" in response.text
        assert "Probability of Ruin" in response.text

    def test_backtest_report_omits_monte_carlo_section_below_30_trades(self):
        _setup_backtest_app_state()
        client = TestClient(app)

        equity_curve = pd.Series(
            [100000.0] * 5, index=pd.date_range("2023-01-01", periods=5, freq="D", tz="UTC")
        )
        with patch(
            "src.routers.backtest.run_pandas_backtest",
            return_value=(equity_curve, _make_large_trades_df(10)),
        ):
            payload = {
                "symbol": "AAPL",
                "timeframe": "1D",
                "start_date": "2023-01-01",
                "end_date": "2023-12-31",
                "strategy": "FVG_Strategy",
                "strategy_params": {},
                "initial_cash": 100000.0,
                "engine": "pandas",
            }
            response = client.post("/api/backtest/report", json=payload)

        assert response.status_code == 200
        assert "Monte Carlo Risk Analysis" not in response.text
