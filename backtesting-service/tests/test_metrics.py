import pandas as pd
import pytest

from src.analysis.metrics import (
    calculate_metrics,
    monte_carlo_simulation,
    run_pandas_backtest,
)


def _equity(values: list[float]) -> pd.Series:
    dates = pd.date_range("2023-01-01", periods=len(values), freq="D", tz="UTC")
    return pd.Series(values, index=dates, dtype=float)


def _trades(pnl_list: list[float]) -> pd.DataFrame:
    n = len(pnl_list)
    entry_dates = pd.date_range("2023-01-01", periods=n, freq="2D", tz="UTC")
    exit_dates = pd.date_range("2023-01-02", periods=n, freq="2D", tz="UTC")
    return pd.DataFrame(
        {
            "entry_time": entry_dates,
            "exit_time": exit_dates,
            "entry_price": [100.0] * n,
            "exit_price": [100.0 + p for p in pnl_list],
            "pnl": pnl_list,
            "side": ["long"] * n,
        }
    )


# ---------------------------------------------------------------------------
# run_pandas_backtest
# ---------------------------------------------------------------------------


class TestRunPandasBacktest:
    def _make_data(self, prices: list[float]) -> pd.DataFrame:
        # Use plain lists — a pd.Series has a RangeIndex which misaligns with
        # the DatetimeIndex and silently fills all values with NaN.
        dates = pd.date_range("2023-01-01", periods=len(prices), freq="D", tz="UTC")
        return pd.DataFrame(
            {
                "open": [p - 0.1 for p in prices],
                "high": [p + 0.5 for p in prices],
                "low": [p - 0.5 for p in prices],
                "close": prices,
                "volume": [1000.0] * len(prices),
            },
            index=dates,
        )

    def _make_signals(self, index, values: list[int]) -> pd.DataFrame:
        return pd.DataFrame({"signal": values}, index=index)

    def test_no_trades_returns_flat_equity(self):
        data = self._make_data([100.0] * 10)
        signals = self._make_signals(data.index, [0] * 10)
        equity, trades = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.0)
        assert len(equity) == 10
        assert (equity == 10000.0).all()
        assert len(trades) == 0

    def test_buy_and_sell_records_trade(self):
        data = self._make_data([100.0, 100.0, 110.0, 110.0])
        sigs = [1, 0, -1, 0]
        signals = self._make_signals(data.index, sigs)
        _, trades = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.0)
        assert len(trades) >= 1
        assert trades.iloc[0]["pnl"] > 0  # bought at 100, sold at 110

    def test_equity_never_goes_negative(self):
        prices = [100.0, 90.0, 80.0, 70.0, 60.0]
        data = self._make_data(prices)
        sigs = [1, 0, 0, 0, -1]
        signals = self._make_signals(data.index, sigs)
        equity, _ = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.0)
        assert (equity >= 0).all()

    def test_fees_reduce_pnl(self):
        data = self._make_data([100.0, 100.0, 100.0])
        sigs = [1, 0, -1]
        signals = self._make_signals(data.index, sigs)
        _, trades_no_fee = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.0)
        _, trades_with_fee = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.01)
        assert trades_with_fee.iloc[0]["pnl"] < trades_no_fee.iloc[0]["pnl"]

    def test_equity_length_matches_data(self):
        data = self._make_data([100.0] * 20)
        signals = self._make_signals(data.index, [0] * 20)
        equity, _ = run_pandas_backtest(data, signals)
        assert len(equity) == len(data)

    def test_open_position_force_closed_at_end(self):
        data = self._make_data([100.0, 105.0, 110.0])
        sigs = [1, 0, 0]  # buy but never sell
        signals = self._make_signals(data.index, sigs)
        _, trades = run_pandas_backtest(data, signals, initial_cash=10000.0, fees=0.0)
        assert len(trades) == 1  # position force-closed at last bar
        assert trades.iloc[0]["pnl"] > 0  # bought at 100, force-closed at 110


# ---------------------------------------------------------------------------
# calculate_metrics
# ---------------------------------------------------------------------------


class TestCalculateMetrics:
    def test_zero_return_on_flat_equity(self):
        equity = _equity([100000.0] * 252)
        trades = _trades([])
        m = calculate_metrics(equity, trades, initial_cash=100000.0)
        assert m.total_return == pytest.approx(0.0, abs=1e-6)
        assert m.total_trades == 0

    def test_positive_return_computed_correctly(self):
        equity = _equity([100000.0, 110000.0])
        m = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0)
        assert m.total_return == pytest.approx(0.1, rel=1e-3)

    def test_max_drawdown_calculated(self):
        # Equity rises to 120k then drops to 90k → drawdown = (90-120)/120 = 25%
        equity = _equity([100000.0, 110000.0, 120000.0, 90000.0, 100000.0])
        m = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0)
        assert m.max_drawdown == pytest.approx(0.25, rel=1e-2)

    def test_win_rate_correct(self):
        trades = _trades([100.0, -50.0, 200.0, -30.0])  # 2 wins, 2 losses
        equity = _equity([100000.0] * 10)
        m = calculate_metrics(equity, trades, initial_cash=100000.0)
        assert m.win_rate == pytest.approx(0.5, rel=1e-3)
        assert m.total_trades == 4

    def test_profit_factor_correct(self):
        trades = _trades([200.0, 100.0, -50.0, -100.0])  # gross profit 300, gross loss 150
        equity = _equity([100000.0] * 10)
        m = calculate_metrics(equity, trades, initial_cash=100000.0)
        assert m.profit_factor == pytest.approx(2.0, rel=1e-2)

    def test_sharpe_zero_on_flat_equity(self):
        equity = _equity([100000.0] * 50)
        m = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0)
        assert m.sharpe_ratio == pytest.approx(0.0, abs=1e-6)

    def test_largest_win_and_loss(self):
        trades = _trades([500.0, 100.0, -300.0, -50.0])
        equity = _equity([100000.0] * 10)
        m = calculate_metrics(equity, trades, initial_cash=100000.0)
        assert m.largest_win == pytest.approx(500.0)
        assert m.largest_loss == pytest.approx(-300.0)

    def test_expectancy_calculation(self):
        # win_rate=0.5, avg_win=100, avg_loss=-50  → expectancy = 0.5*100 + 0.5*(-50) = 25
        trades = _trades([100.0, 100.0, -50.0, -50.0])
        equity = _equity([100000.0] * 10)
        m = calculate_metrics(equity, trades, initial_cash=100000.0)
        assert m.expectancy == pytest.approx(25.0, rel=1e-2)

    def test_empty_equity_returns_zero_metrics(self):
        m = calculate_metrics(pd.Series([], dtype=float), pd.DataFrame())
        assert m.total_return == 0.0
        assert m.total_trades == 0

    def test_max_drawdown_duration_counted(self):
        # Equity falls for 5 bars then recovers
        values = [100000.0, 90000.0, 85000.0, 80000.0, 75000.0, 70000.0, 110000.0]
        equity = _equity(values)
        m = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0)
        assert m.max_drawdown_duration >= 5

    def test_sharpe_annualisation_differs_by_timeframe(self):
        # 1H bars have sqrt(1638 / 252) ≈ 2.55x the annualisation factor of 1D bars.
        # The same equity curve must therefore produce a higher absolute Sharpe for 1H.
        equity = _equity([100000.0, 102000.0, 101000.0, 103000.0, 102000.0, 104000.0])
        m_daily = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0, timeframe="1D")
        m_hourly = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0, timeframe="1H")
        assert m_hourly.sharpe_ratio != pytest.approx(m_daily.sharpe_ratio, rel=1e-3)
        assert abs(m_hourly.sharpe_ratio) > abs(m_daily.sharpe_ratio)

    def test_unknown_timeframe_warns_and_defaults_to_daily(self, caplog):
        import logging

        equity = _equity([100000.0, 102000.0, 101000.0])
        with caplog.at_level(logging.WARNING):
            m = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0, timeframe="bad_tf")
        assert any("bad_tf" in r.message for r in caplog.records)
        # Fallback must equal the explicit daily result
        m_daily = calculate_metrics(equity, pd.DataFrame(), initial_cash=100000.0, timeframe="1D")
        assert m.sharpe_ratio == pytest.approx(m_daily.sharpe_ratio)


# ---------------------------------------------------------------------------
# monte_carlo_simulation
# ---------------------------------------------------------------------------


class TestMonteCarloSimulation:
    def test_returns_zero_on_empty_trades(self):
        result = monte_carlo_simulation(pd.DataFrame(), num_simulations=100)
        assert result.median_return == 0.0
        assert result.drawdown_95th == 0.0
        assert result.risk_of_ruin == 0.0
        assert result.simulations == 0

    def test_positive_expectancy_reduces_ruin(self):
        # Strong positive trades → very low risk of ruin
        trades = _trades([500.0] * 20)
        result = monte_carlo_simulation(trades, initial_cash=100000.0, num_simulations=500)
        assert result.risk_of_ruin < 0.05

    def test_catastrophic_losses_increase_ruin(self):
        # All trades are large losses → near-certain ruin
        trades = _trades([-10000.0] * 20)
        result = monte_carlo_simulation(trades, initial_cash=100000.0, num_simulations=500)
        assert result.risk_of_ruin > 0.9

    def test_drawdown_95th_is_between_0_and_1(self):
        trades = _trades([100.0, -50.0] * 10)
        result = monte_carlo_simulation(trades, initial_cash=100000.0, num_simulations=200)
        assert 0.0 <= result.drawdown_95th <= 1.0

    def test_simulations_count_matches_request(self):
        trades = _trades([100.0, -50.0] * 5)
        result = monte_carlo_simulation(trades, num_simulations=123)
        assert result.simulations == 123

    def test_deterministic_with_seed(self):
        """Two runs should produce identical results (fixed seed inside implementation)."""
        trades = _trades([200.0, -80.0, 150.0, -40.0])
        r1 = monte_carlo_simulation(trades, num_simulations=100)
        r2 = monte_carlo_simulation(trades, num_simulations=100)
        assert r1.median_return == r2.median_return
        assert r1.risk_of_ruin == r2.risk_of_ruin

    def test_path_level_ruin_detected_when_equity_recovers(self):
        # A large loss (-600) can drop equity below the 50% ruin threshold mid-sequence
        # even when subsequent gains (+700) bring the final value back above it.
        # With bootstrap sampling (replace=True), ~50% of sequences see the -600 first
        # and trigger path-level ruin; a final-value-only check would only catch ~25%.
        # We assert > 0.30 to confirm np.any (path-level) is active, not equity[-1].
        trades = _trades([-600.0, 700.0])
        result = monte_carlo_simulation(trades, initial_cash=1000.0, num_simulations=500)
        assert result.risk_of_ruin > 0.30
