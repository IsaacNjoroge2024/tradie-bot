import logging

import pandas as pd

from .vectorbt_engine import BacktestResult

logger = logging.getLogger(__name__)


class BacktraderEngineError(Exception):
    pass


class BacktraderEngine:
    """
    Backtesting engine backed by Backtrader for event-driven simulation.

    Backtrader must be installed via the [backtesting] extra:
        pip install -e ".[backtesting]"

    This engine is provided for IBKR compatibility testing since Backtrader
    natively supports IBKR paper/live data feeds.  For speed-critical batch
    optimisation use VectorBTEngine instead.
    """

    def run_backtest(
        self,
        data: pd.DataFrame,
        signals: pd.DataFrame,
        initial_cash: float = 100000.0,
        fees: float = 0.001,
        slippage: float = 0.0005,
    ) -> BacktestResult:
        if data.empty:
            raise BacktraderEngineError("run_backtest requires non-empty OHLCV data")

        try:
            import backtrader as bt
        except ImportError as exc:
            raise BacktraderEngineError(
                "backtrader is not installed. Install it with: pip install -e '.[backtesting]'"
            ) from exc

        # --- Strategy class defined here so `bt` is in scope ---
        class _SignalStrategy(bt.Strategy):  # type: ignore[misc]
            def __init__(self_s) -> None:
                self_s.equity_values: list[float] = []
                self_s.closed_trades: list[dict] = []
                self_s._entry_size: float = 0.0

            def next(self_s) -> None:
                self_s.equity_values.append(float(self_s.broker.getvalue()))
                signal = float(self_s.data.signal[0])
                if not self_s.position and signal == 1.0:
                    self_s.buy()
                elif self_s.position and signal == -1.0:
                    self_s.close()

            def notify_order(self_s, order) -> None:
                if order.status == order.Completed and order.isbuy():
                    self_s._entry_size = float(order.executed.size)

            def notify_trade(self_s, trade) -> None:
                if not trade.isclosed:
                    return
                entry_time = bt.num2date(trade.dtopen)
                exit_time = bt.num2date(trade.dtclose)
                entry_price = float(trade.price)
                size = self_s._entry_size
                pnl = float(trade.pnlcomm)
                exit_price = entry_price + float(trade.pnl) / size if size > 0 else entry_price
                self_s.closed_trades.append(
                    {
                        "entry_time": entry_time,
                        "exit_time": exit_time,
                        "entry_price": entry_price,
                        "exit_price": exit_price,
                        "pnl": pnl,
                        "side": "long",
                    }
                )

        # --- Data feed with pre-computed signal line ---
        class _SignalPandasData(bt.feeds.PandasData):  # type: ignore[misc]
            lines = ("signal",)
            params = (("signal", -1),)

        merged = data.copy()
        merged["signal"] = signals["signal"].reindex(data.index).fillna(0)

        cerebro = bt.Cerebro()
        cerebro.broker.setcash(initial_cash)
        cerebro.broker.setcommission(commission=fees)
        cerebro.broker.set_slippage_perc(slippage)

        cerebro.adddata(_SignalPandasData(dataname=merged))
        cerebro.addstrategy(_SignalStrategy)

        cerebro.addanalyzer(bt.analyzers.SharpeRatio, _name="sharpe", riskfreerate=0.0)
        cerebro.addanalyzer(bt.analyzers.DrawDown, _name="drawdown")
        cerebro.addanalyzer(bt.analyzers.TradeAnalyzer, _name="trades")

        results = cerebro.run()
        strat = results[0]

        final_value = float(cerebro.broker.getvalue())
        total_return = (final_value - initial_cash) / initial_cash

        sharpe_analysis = strat.analyzers.sharpe.get_analysis()
        sharpe_ratio = float(sharpe_analysis.get("sharperatio") or 0.0)

        dd_analysis = strat.analyzers.drawdown.get_analysis()
        max_drawdown = float(dd_analysis.get("max", {}).get("drawdown", 0.0)) / 100.0

        trade_analysis = strat.analyzers.trades.get_analysis()
        total_trades = int(trade_analysis.get("total", {}).get("closed", 0))
        won = int(trade_analysis.get("won", {}).get("total", 0))
        win_rate = float(won / total_trades) if total_trades > 0 else 0.0

        pnl_won = float(trade_analysis.get("won", {}).get("pnl", {}).get("total", 0.0))
        pnl_lost = abs(float(trade_analysis.get("lost", {}).get("pnl", {}).get("total", 0.0)))
        profit_factor = float(pnl_won / pnl_lost) if pnl_lost > 0 else 0.0

        days = max((data.index[-1] - data.index[0]).days, 1)
        annualized = float((1.0 + total_return) ** (365.0 / days) - 1.0)

        equity_curve = pd.Series(
            strat.equity_values,
            index=data.index[: len(strat.equity_values)],
            dtype=float,
        )

        _schema = ["entry_time", "exit_time", "entry_price", "exit_price", "pnl", "side"]
        trades_df = (
            pd.DataFrame(strat.closed_trades)
            if strat.closed_trades
            else pd.DataFrame(columns=_schema)
        )

        return BacktestResult(
            total_return=total_return,
            annualized_return=annualized,
            sharpe_ratio=sharpe_ratio,
            sortino_ratio=0.0,  # Backtrader has no built-in Sortino analyser
            max_drawdown=max_drawdown,
            win_rate=win_rate,
            profit_factor=profit_factor,
            total_trades=total_trades,
            equity_curve=equity_curve,
            trades=trades_df,
        )
