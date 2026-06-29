import pandas as pd
import numpy as np
import pytest

from src.optimization.grid_search import GridSearchOptimizer
from src.optimization.walk_forward import WalkForwardAnalyzer
from src.strategies.fvg_strategy import FVGStrategy
from src.strategies.confluence_strategy import ConfluenceStrategy


def _make_data(n: int = 200, seed: int = 1) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    dates = pd.date_range("2022-01-01", periods=n, freq="D", tz="UTC")
    close = np.cumsum(rng.normal(0, 0.5, n)) + 100.0
    close = np.maximum(close, 10.0)
    return pd.DataFrame(
        {
            "open": close - 0.2,
            "high": close + 1.0,
            "low": close - 1.0,
            "close": close,
            "volume": rng.integers(500, 5000, n).astype(float),
        },
        index=dates,
    )


# ---------------------------------------------------------------------------
# GridSearchOptimizer
# ---------------------------------------------------------------------------


class TestGridSearchOptimizer:
    def test_returns_optimization_result(self):
        data = _make_data()
        opt = GridSearchOptimizer()
        result = opt.optimize(
            FVGStrategy,
            data,
            {"min_gap_atr": [0.0, 0.3]},
            optimize_metric="sharpe_ratio",
        )
        assert "min_gap_atr" in result.best_params
        assert isinstance(result.best_metric_value, float)

    def test_all_results_count_matches_grid(self):
        data = _make_data()
        opt = GridSearchOptimizer()
        result = opt.optimize(
            FVGStrategy,
            data,
            {"min_gap_atr": [0.0, 0.5, 1.0], "atr_period": [10, 14]},
            optimize_metric="sharpe_ratio",
        )
        assert len(result.all_results) == 6  # 3 × 2

    def test_optimize_metric_in_result(self):
        data = _make_data()
        opt = GridSearchOptimizer()
        result = opt.optimize(
            FVGStrategy,
            data,
            {"min_gap_atr": [0.0, 0.3]},
            optimize_metric="total_return",
        )
        assert result.optimize_metric == "total_return"

    def test_best_params_in_all_results(self):
        data = _make_data()
        opt = GridSearchOptimizer()
        result = opt.optimize(
            FVGStrategy,
            data,
            {"min_gap_atr": [0.0, 0.5]},
            optimize_metric="sharpe_ratio",
        )
        best_in_all = any(r["params"] == result.best_params for r in result.all_results)
        assert best_in_all

    def test_raises_on_empty_param_grid(self):
        data = _make_data()
        opt = GridSearchOptimizer()
        with pytest.raises(ValueError, match="param_grid must not be empty"):
            opt.optimize(FVGStrategy, data, {})

    def test_confluence_strategy_optimizable(self):
        data = _make_data(n=150)
        opt = GridSearchOptimizer()
        result = opt.optimize(
            ConfluenceStrategy,
            data,
            {"min_gap_atr": [0.0], "rsi_period": [10, 14]},
            optimize_metric="sharpe_ratio",
        )
        assert "rsi_period" in result.best_params


# ---------------------------------------------------------------------------
# WalkForwardAnalyzer
# ---------------------------------------------------------------------------


class TestWalkForwardAnalyzer:
    def test_requires_minimum_bars(self):
        data = _make_data(n=5)
        wf = WalkForwardAnalyzer(num_periods=3)
        with pytest.raises(ValueError, match="at least"):
            wf.run(FVGStrategy, data, {"min_gap_atr": [0.0]})

    def test_returns_walk_forward_result(self):
        data = _make_data(n=300)
        wf = WalkForwardAnalyzer(in_sample_pct=0.7, num_periods=3)
        result = wf.run(FVGStrategy, data, {"min_gap_atr": [0.0, 0.3]})
        assert "combined_return" in result.__dataclass_fields__
        assert "consistency" in result.__dataclass_fields__
        assert "period_results" in result.__dataclass_fields__

    def test_consistency_between_0_and_1(self):
        data = _make_data(n=300)
        wf = WalkForwardAnalyzer(in_sample_pct=0.7, num_periods=3)
        result = wf.run(FVGStrategy, data, {"min_gap_atr": [0.0, 0.3]})
        assert 0.0 <= result.consistency <= 1.0

    def test_period_results_count_not_exceeding_num_periods(self):
        data = _make_data(n=300)
        wf = WalkForwardAnalyzer(in_sample_pct=0.7, num_periods=4)
        result = wf.run(FVGStrategy, data, {"min_gap_atr": [0.0]})
        assert len(result.period_results) <= 4

    def test_invalid_in_sample_pct_raises(self):
        with pytest.raises(ValueError):
            WalkForwardAnalyzer(in_sample_pct=0.0)
        with pytest.raises(ValueError):
            WalkForwardAnalyzer(in_sample_pct=1.0)

    def test_invalid_num_periods_raises(self):
        with pytest.raises(ValueError):
            WalkForwardAnalyzer(num_periods=1)

    def test_period_results_have_required_keys(self):
        data = _make_data(n=300)
        wf = WalkForwardAnalyzer(in_sample_pct=0.7, num_periods=3)
        result = wf.run(FVGStrategy, data, {"min_gap_atr": [0.0]})
        for pr in result.period_results:
            assert "best_params" in pr
            assert "total_return" in pr
            assert "sharpe_ratio" in pr
            assert "max_drawdown" in pr

    def test_split_periods_correct_count(self):
        data = _make_data(n=200)
        wf = WalkForwardAnalyzer(in_sample_pct=0.7, num_periods=4)
        periods = wf._split_periods(data)
        assert len(periods) <= 4
        for in_s, out_s in periods:
            assert len(in_s) > 0
            assert len(out_s) > 0
