import logging
from dataclasses import dataclass
from typing import Any, Type

import numpy as np
import pandas as pd

from ..analysis.metrics import calculate_metrics, run_pandas_backtest
from ..strategies.base import BaseStrategy
from .grid_search import GridSearchOptimizer

logger = logging.getLogger(__name__)


@dataclass
class WalkForwardResult:
    combined_return: float
    consistency: float
    period_results: list[dict[str, Any]]


class WalkForwardAnalyzer:
    """
    Walk-forward analysis: divides data into N equal periods, optimises
    on the in-sample portion of each period, then tests on the out-of-sample
    portion.  Robustness is measured by consistency (fraction of profitable
    out-of-sample periods) and a multiplicatively combined total return.
    """

    def __init__(
        self,
        in_sample_pct: float = 0.7,
        num_periods: int = 6,
    ) -> None:
        if not 0.0 < in_sample_pct < 1.0:
            raise ValueError("in_sample_pct must be between 0 and 1 (exclusive)")
        if num_periods < 2:
            raise ValueError("num_periods must be at least 2")

        self.in_sample_pct = in_sample_pct
        self.num_periods = num_periods

    def run(
        self,
        strategy_class: Type[BaseStrategy],
        data: pd.DataFrame,
        param_grid: dict[str, list],
        initial_cash: float = 100000.0,
        fees: float = 0.001,
        timeframe: str = "1D",
    ) -> WalkForwardResult:
        min_bars = self.num_periods * 20
        if len(data) < min_bars:
            raise ValueError(
                f"Walk-forward requires at least {min_bars} bars "
                f"(num_periods={self.num_periods} × 20); got {len(data)}"
            )

        periods = self._split_periods(data)
        if not periods:
            raise ValueError("Failed to split data into valid in/out-sample periods")

        optimizer = GridSearchOptimizer()
        period_results: list[dict[str, Any]] = []

        for i, (in_sample, out_sample) in enumerate(periods):
            try:
                opt = optimizer.optimize(
                    strategy_class,
                    in_sample,
                    param_grid,
                    initial_cash,
                    fees,
                    timeframe=timeframe,
                )
                strategy = strategy_class(**opt.best_params)
                signals = strategy.generate_signals(out_sample)
                equity_curve, trades = run_pandas_backtest(out_sample, signals, initial_cash, fees)
                metrics = calculate_metrics(equity_curve, trades, initial_cash, timeframe)

                period_results.append(
                    {
                        "period": i + 1,
                        "in_sample_bars": len(in_sample),
                        "out_sample_bars": len(out_sample),
                        "best_params": opt.best_params,
                        "total_return": metrics.total_return,
                        "sharpe_ratio": metrics.sharpe_ratio,
                        "max_drawdown": metrics.max_drawdown,
                        "total_trades": metrics.total_trades,
                        "win_rate": metrics.win_rate,
                    }
                )
            except Exception as exc:
                logger.warning(f"Walk-forward period {i + 1} failed: {exc}")
                period_results.append(
                    {
                        "period": i + 1,
                        "in_sample_bars": len(in_sample),
                        "out_sample_bars": len(out_sample),
                        "best_params": {},
                        "total_return": 0.0,
                        "sharpe_ratio": 0.0,
                        "max_drawdown": 0.0,
                        "total_trades": 0,
                        "win_rate": 0.0,
                        "error": str(exc),
                    }
                )

        if not any("error" not in r for r in period_results):
            raise ValueError("All walk-forward periods failed")

        returns = [r["total_return"] for r in period_results]
        combined_return = float(np.prod([1.0 + r for r in returns]) - 1.0)
        consistency = float(np.mean([1.0 if r > 0 else 0.0 for r in returns]))

        return WalkForwardResult(
            combined_return=round(combined_return, 6),
            consistency=round(consistency, 4),
            period_results=period_results,
        )

    def _split_periods(self, data: pd.DataFrame) -> list[tuple[pd.DataFrame, pd.DataFrame]]:
        n = len(data)
        period_size = n // self.num_periods
        periods: list[tuple[pd.DataFrame, pd.DataFrame]] = []

        for i in range(self.num_periods):
            start = i * period_size
            end = (i + 1) * period_size if i < self.num_periods - 1 else n
            period_data = data.iloc[start:end]

            split_idx = int(len(period_data) * self.in_sample_pct)
            in_sample = period_data.iloc[:split_idx]
            out_sample = period_data.iloc[split_idx:]

            if len(in_sample) >= 10 and len(out_sample) >= 3:
                periods.append((in_sample, out_sample))

        return periods
