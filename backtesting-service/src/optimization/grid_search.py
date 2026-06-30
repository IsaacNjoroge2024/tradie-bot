import logging
from dataclasses import dataclass
from itertools import product as cartesian_product
from typing import Any, Type

import pandas as pd

from ..analysis.metrics import BacktestMetrics, calculate_metrics, run_pandas_backtest
from ..strategies.base import BaseStrategy

logger = logging.getLogger(__name__)


@dataclass
class OptimizationResult:
    best_params: dict[str, Any]
    best_metric_value: float
    optimize_metric: str
    all_results: list[dict[str, Any]]


class GridSearchOptimizer:
    """
    Exhaustive grid search over a strategy's parameter space.

    Evaluates every combination from param_grid, ranking by the chosen metric.
    Uses the pure-pandas backtester so it runs without heavy dependencies.
    """

    def optimize(
        self,
        strategy_class: Type[BaseStrategy],
        data: pd.DataFrame,
        param_grid: dict[str, list],
        initial_cash: float = 100000.0,
        fees: float = 0.001,
        optimize_metric: str = "sharpe_ratio",
        timeframe: str = "1D",
    ) -> OptimizationResult:
        if not param_grid:
            raise ValueError("param_grid must not be empty")
        if optimize_metric not in BacktestMetrics.__dataclass_fields__:
            raise ValueError(f"Unknown optimize_metric '{optimize_metric}'")

        param_names = list(param_grid.keys())
        param_values = list(param_grid.values())
        combinations = list(cartesian_product(*param_values))

        if len(combinations) == 0:
            raise ValueError("param_grid produced zero combinations")

        results: list[dict[str, Any]] = []

        for combo in combinations:
            params = dict(zip(param_names, combo))
            try:
                strategy = strategy_class(**params)
                signals = strategy.generate_signals(data)
                equity_curve, trades = run_pandas_backtest(data, signals, initial_cash, fees)
                metrics = calculate_metrics(equity_curve, trades, initial_cash, timeframe)
                metric_value = float(getattr(metrics, optimize_metric))
                results.append(
                    {
                        "params": params,
                        "metric_value": metric_value,
                        "total_return": metrics.total_return,
                        "sharpe_ratio": metrics.sharpe_ratio,
                        "max_drawdown": metrics.max_drawdown,
                        "total_trades": metrics.total_trades,
                        "win_rate": metrics.win_rate,
                    }
                )
            except Exception as exc:
                logger.warning(f"Skipping params {params}: {exc}")

        if not results:
            raise ValueError(f"All parameter combinations failed for {strategy_class.__name__}")

        best = max(results, key=lambda r: r["metric_value"])

        return OptimizationResult(
            best_params=best["params"],
            best_metric_value=best["metric_value"],
            optimize_metric=optimize_metric,
            all_results=results,
        )
