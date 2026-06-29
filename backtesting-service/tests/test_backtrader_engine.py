import numpy as np
import pandas as pd
import pytest

# Skip the entire module if backtrader is not installed (it lives in the [backtesting] extra).
pytest.importorskip(
    "backtrader", reason="backtrader not installed; run: pip install -e '.[backtesting]'"
)

from src.engine.backtrader_engine import BacktraderEngine  # noqa: E402


def _make_data(n: int = 50) -> pd.DataFrame:
    np.random.seed(42)
    dates = pd.date_range("2023-01-01", periods=n, freq="D", tz="UTC")
    close = 100.0 + np.cumsum(np.random.randn(n) * 0.5)
    return pd.DataFrame(
        {
            "open": close - 0.2,
            "high": close + 0.5,
            "low": close - 0.5,
            "close": close,
            "volume": np.ones(n) * 1000.0,
        },
        index=dates,
    )


def _make_signals(data: pd.DataFrame, buy_at: list, sell_at: list) -> pd.DataFrame:
    signals = pd.DataFrame({"signal": 0}, index=data.index)
    for i in buy_at:
        signals.iloc[i, 0] = 1
    for i in sell_at:
        signals.iloc[i, 0] = -1
    return signals


_SCHEMA = {"entry_time", "exit_time", "entry_price", "exit_price", "pnl", "side"}


class TestBacktraderEngineTrades:
    def test_returns_backtest_result(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        assert hasattr(result, "equity_curve")
        assert hasattr(result, "trades")
        assert hasattr(result, "total_return")

    def test_trades_have_correct_schema(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        assert _SCHEMA.issubset(set(result.trades.columns))

    def test_one_complete_trade_captured(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        assert len(result.trades) == 1

    def test_two_complete_trades_captured(self):
        engine = BacktraderEngine()
        data = _make_data(60)
        signals = _make_signals(data, buy_at=[5, 30], sell_at=[20, 45])
        result = engine.run_backtest(data, signals)
        assert len(result.trades) == 2

    def test_no_signals_returns_empty_trades_with_schema(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[], sell_at=[])
        result = engine.run_backtest(data, signals)
        assert result.trades.empty
        assert _SCHEMA.issubset(set(result.trades.columns))

    def test_pnl_is_numeric(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        if not result.trades.empty:
            assert pd.api.types.is_numeric_dtype(result.trades["pnl"])

    def test_side_is_long(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        if not result.trades.empty:
            assert (result.trades["side"] == "long").all()

    def test_entry_time_before_exit_time(self):
        engine = BacktraderEngine()
        data = _make_data()
        signals = _make_signals(data, buy_at=[5], sell_at=[20])
        result = engine.run_backtest(data, signals)
        if not result.trades.empty:
            entry = pd.to_datetime(result.trades["entry_time"])
            exit_ = pd.to_datetime(result.trades["exit_time"])
            assert (entry < exit_).all()

    def test_raises_on_empty_data(self):
        from src.engine.backtrader_engine import BacktraderEngineError

        engine = BacktraderEngine()
        empty_data = pd.DataFrame(columns=["open", "high", "low", "close", "volume"])
        empty_signals = pd.DataFrame(columns=["signal"])
        with pytest.raises(BacktraderEngineError, match="non-empty OHLCV data"):
            engine.run_backtest(empty_data, empty_signals)
