import logging
from dataclasses import dataclass

import pandas as pd

logger = logging.getLogger(__name__)

_TF_TO_FREQ: dict[str, str] = {
    "1m": "1min",
    "5m": "5min",
    "15m": "15min",
    "30m": "30min",
    "1H": "1h",
    "2H": "2h",
    "4H": "4h",
    "1D": "1D",
    "1W": "1W",
}


def _timeframe_to_freq(timeframe: str) -> str:
    """Map tradie timeframe string to a VectorBT/pandas offset alias."""
    return _TF_TO_FREQ.get(timeframe, timeframe)


class BacktestEngineError(Exception):
    pass


@dataclass
class BacktestResult:
    total_return: float
    annualized_return: float
    sharpe_ratio: float
    sortino_ratio: float
    max_drawdown: float
    win_rate: float
    profit_factor: float
    total_trades: int
    equity_curve: pd.Series
    trades: pd.DataFrame


class VectorBTEngine:
    """
    Backtesting engine backed by VectorBT for fast vectorized portfolio simulation.

    VectorBT must be installed via the [backtesting] extra:
        pip install -e ".[backtesting]"

    Falls back to a pure-pandas simulation when VectorBT is not installed so
    that the service can still start without the heavy dependency.
    """

    def run_backtest(
        self,
        data: pd.DataFrame,
        signals: pd.DataFrame,
        initial_cash: float = 100000.0,
        fees: float = 0.001,
        slippage: float = 0.0005,
        timeframe: str = "1D",
    ) -> BacktestResult:
        try:
            return self._run_with_vectorbt(data, signals, initial_cash, fees, slippage, timeframe)
        except ImportError:
            logger.warning(
                "vectorbt is not installed; falling back to pandas engine. "
                "Install it with: pip install -e '.[backtesting]'"
            )
            return self._run_with_pandas(data, signals, initial_cash, fees, slippage, timeframe)

    def _run_with_vectorbt(
        self,
        data: pd.DataFrame,
        signals: pd.DataFrame,
        initial_cash: float,
        fees: float,
        slippage: float,
        timeframe: str = "1D",
    ) -> BacktestResult:
        import vectorbt as vbt  # noqa: PLC0415

        entries = signals["signal"] == 1
        exits = signals["signal"] == -1

        portfolio = vbt.Portfolio.from_signals(
            close=data["close"],
            entries=entries,
            exits=exits,
            init_cash=initial_cash,
            fees=fees + slippage,
            freq=_timeframe_to_freq(timeframe),
        )

        equity_curve = portfolio.value()

        # Extract trade-level stats — API varies slightly by vectorbt version
        try:
            trades_pnl = portfolio.trades.pnl.values
            wins = trades_pnl[trades_pnl > 0]
            losses = trades_pnl[trades_pnl < 0]
            win_rate = float(len(wins) / len(trades_pnl)) if len(trades_pnl) > 0 else 0.0
            profit_factor = (
                float(wins.sum() / abs(losses.sum()))
                if len(losses) > 0 and losses.sum() != 0
                else 0.0
            )
            total_trades = int(len(trades_pnl))
            raw = portfolio.trades.records_readable
            col_map = {
                "Entry Timestamp": "entry_time",
                "Exit Timestamp": "exit_time",
                "Avg Entry Price": "entry_price",
                "Avg Exit Price": "exit_price",
                "PnL": "pnl",
            }
            raw = raw.rename(columns=col_map)
            raw["side"] = raw["Direction"].str.lower() if "Direction" in raw.columns else "long"
            schema_cols = ["entry_time", "exit_time", "entry_price", "exit_price", "pnl", "side"]
            trades_df = raw[[c for c in schema_cols if c in raw.columns]]
        except Exception:
            win_rate = 0.0
            profit_factor = 0.0
            total_trades = 0
            trades_df = pd.DataFrame()

        total_return = float(portfolio.total_return())
        sharpe = float(portfolio.sharpe_ratio())
        sortino = float(portfolio.sortino_ratio())
        max_dd = float(portfolio.max_drawdown())

        days = (equity_curve.index[-1] - equity_curve.index[0]).days
        annualized = float((1 + total_return) ** (365 / days) - 1) if days > 0 else 0.0

        return BacktestResult(
            total_return=total_return,
            annualized_return=annualized,
            sharpe_ratio=sharpe,
            sortino_ratio=sortino,
            max_drawdown=max_dd,
            win_rate=win_rate,
            profit_factor=profit_factor,
            total_trades=total_trades,
            equity_curve=equity_curve,
            trades=trades_df,
        )

    def _run_with_pandas(
        self,
        data: pd.DataFrame,
        signals: pd.DataFrame,
        initial_cash: float,
        fees: float,
        slippage: float,
        timeframe: str = "1D",
    ) -> BacktestResult:
        from ..analysis.metrics import calculate_metrics, run_pandas_backtest

        equity_curve, trades_df = run_pandas_backtest(data, signals, initial_cash, fees + slippage)
        metrics = calculate_metrics(equity_curve, trades_df, initial_cash, timeframe)

        return BacktestResult(
            total_return=metrics.total_return,
            annualized_return=metrics.annualized_return,
            sharpe_ratio=metrics.sharpe_ratio,
            sortino_ratio=metrics.sortino_ratio,
            max_drawdown=metrics.max_drawdown,
            win_rate=metrics.win_rate,
            profit_factor=metrics.profit_factor,
            total_trades=metrics.total_trades,
            equity_curve=equity_curve,
            trades=trades_df,
        )
