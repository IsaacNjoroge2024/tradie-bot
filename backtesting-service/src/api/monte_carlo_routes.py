from typing import List
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from ..monte_carlo.models import SimulationConfig, Trade
from ..monte_carlo.simulator import MonteCarloSimulator
from ..monte_carlo.stress_tests import StressTestSuite
from ..monte_carlo.thresholds import THRESHOLDS

router = APIRouter(prefix="/api/monte-carlo", tags=["Monte Carlo"])


class MonteCarloRequest(BaseModel):
    trades: List[dict]  # Trade data from backtest
    initial_balance: float = 10000.0
    num_simulations: int = THRESHOLDS.default_num_simulations
    ruin_threshold_pct: float = THRESHOLDS.default_ruin_threshold_pct
    skip_trade_pct: float = 0.0
    position_sizing: str = THRESHOLDS.default_position_sizing


class StressTestRequest(BaseModel):
    trades: List[dict]
    initial_balance: float = 10000.0
    num_simulations: int = THRESHOLDS.stress_test_num_simulations  # Fewer — many scenarios


def _dict_to_trade(t: dict, idx: int) -> Trade:
    """Helper to convert a trade dict into a Trade object cleanly."""
    return Trade(
        trade_id=str(t.get("trade_id", f"T{idx}")),
        symbol=str(t.get("symbol", "UNKNOWN")),
        side=str(t.get("side", t.get("type", "BUY"))).upper(),
        entry_price=float(t.get("entry_price", 0.0)),
        exit_price=float(t.get("exit_price", 0.0)),
        quantity=float(t.get("quantity", t.get("size", 1.0))),
        pnl=float(t.get("pnl", 0.0)),
        pnl_pct=float(t.get("pnl_pct", t.get("return_pct", 0.0))),
        hold_time_minutes=int(t.get("hold_time_minutes", t.get("duration_minutes", 0))),
        strategy=str(t.get("strategy", "BACKTEST")),
        timestamp=str(t.get("timestamp", t.get("entry_time", ""))),
    )


@router.post("/simulate", response_model=dict)
async def run_simulation(request: MonteCarloRequest):
    """Run Monte Carlo simulation on trade history"""

    if len(request.trades) < 30:
        raise HTTPException(400, "Need at least 30 trades for Monte Carlo analysis")

    # Convert to Trade objects
    trades = [_dict_to_trade(t, i) for i, t in enumerate(request.trades)]

    config = SimulationConfig(
        initial_balance=request.initial_balance,
        num_simulations=request.num_simulations,
        ruin_threshold_pct=request.ruin_threshold_pct,
        skip_trade_pct=request.skip_trade_pct,
        position_sizing=request.position_sizing,
    )

    simulator = MonteCarloSimulator(config)
    result = simulator.run(trades)

    return {
        "recommendation": result.recommendation,
        "rejection_reasons": result.rejection_reasons,
        "probability_of_profit": result.probability_of_profit,
        "probability_of_ruin": result.probability_of_ruin,
        "median_final_balance": result.median_final_balance,
        "var_95": result.var_95,
        "percentile_95_max_drawdown": result.percentile_95_max_drawdown,
        "worst_losing_streak": result.worst_losing_streak,
        "expected_sharpe": result.expected_sharpe,
    }


@router.post("/stress-test", response_model=dict)
async def run_stress_tests(request: StressTestRequest):
    """Run multiple stress test scenarios"""

    if len(request.trades) < 30:
        raise HTTPException(400, "Need at least 30 trades for Monte Carlo stress testing")

    trades = [_dict_to_trade(t, i) for i, t in enumerate(request.trades)]

    config = SimulationConfig(
        initial_balance=request.initial_balance, num_simulations=request.num_simulations
    )

    suite = StressTestSuite()
    results = suite.run_all_scenarios(trades, config)

    summary = {}
    for scenario_name, result in results.items():
        summary[scenario_name] = {
            "recommendation": result.recommendation,
            "probability_of_ruin": result.probability_of_ruin,
            "percentile_95_max_drawdown": result.percentile_95_max_drawdown,
            "median_final_balance": result.median_final_balance,
        }

    return summary
