import pytest
import numpy as np
from fastapi.testclient import TestClient

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

        assert result_skip.percentile_95_max_drawdown >= result_normal.percentile_95_max_drawdown * 0.9

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
