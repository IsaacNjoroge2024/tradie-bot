import logging
from dataclasses import dataclass

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

# Bars per trading year for each supported timeframe.
# Intraday values use US session hours (6.5 h/day × 252 trading days)
# which is the standard quant-finance convention for multi-asset strategies.
_BARS_PER_YEAR: dict[str, float] = {
    "1m": 252.0 * 390,  # 390 min/trading day
    "5m": 252.0 * 78,
    "15m": 252.0 * 26,
    "30m": 252.0 * 13,
    "1H": 252.0 * 6.5,
    "2H": 252.0 * 3.25,
    "4H": 252.0 * 1.625,
    "1D": 252.0,
    "1W": 52.0,
}


@dataclass
class BacktestMetrics:
    total_return: float
    annualized_return: float
    sharpe_ratio: float
    sortino_ratio: float
    max_drawdown: float
    max_drawdown_duration: int
    win_rate: float
    profit_factor: float
    avg_win: float
    avg_loss: float
    largest_win: float
    largest_loss: float
    total_trades: int
    avg_trade_duration: float
    expectancy: float


@dataclass
class MonteCarloResult:
    median_return: float
    drawdown_95th: float
    risk_of_ruin: float
    simulations: int


def run_pandas_backtest(
    data: pd.DataFrame,
    signals: pd.DataFrame,
    initial_cash: float = 100000.0,
    fees: float = 0.001,
) -> tuple[pd.Series, pd.DataFrame]:
    """
    Pure-pandas long-only backtester.

    Returns:
        equity_curve: pd.Series of portfolio value indexed by data.index
        trades: pd.DataFrame with columns entry_time, exit_time, entry_price,
                exit_price, pnl, side
    """
    cash = initial_cash
    shares = 0.0
    entry_price = 0.0
    entry_time = None
    trades: list[dict] = []
    equity_values: list[float] = []

    signal_col = signals["signal"]

    for idx in data.index:
        price = float(data.loc[idx, "close"])
        signal = int(signal_col.get(idx, 0))

        if shares == 0.0 and signal == 1:
            # Enter long — account for entry fees
            buy_price = price * (1.0 + fees)
            shares = cash / buy_price
            entry_price = buy_price
            entry_time = idx
            cash = 0.0

        elif shares > 0.0 and signal == -1:
            # Exit long — account for exit fees
            sell_price = price * (1.0 - fees)
            proceeds = shares * sell_price
            pnl = proceeds - shares * entry_price
            trades.append(
                {
                    "entry_time": entry_time,
                    "exit_time": idx,
                    "entry_price": entry_price,
                    "exit_price": sell_price,
                    "pnl": pnl,
                    "side": "long",
                }
            )
            cash = proceeds
            shares = 0.0
            entry_price = 0.0
            entry_time = None

        equity_values.append(cash + shares * price)

    # Force-close any open position at the last bar
    if shares > 0.0 and entry_time is not None:
        last_idx = data.index[-1]
        last_price = float(data.loc[last_idx, "close"]) * (1.0 - fees)
        proceeds = shares * last_price
        pnl = proceeds - shares * entry_price
        trades.append(
            {
                "entry_time": entry_time,
                "exit_time": last_idx,
                "entry_price": entry_price,
                "exit_price": last_price,
                "pnl": pnl,
                "side": "long",
            }
        )
        cash = proceeds
        shares = 0.0
        equity_values[-1] = cash

    equity_curve = pd.Series(equity_values, index=data.index, dtype=float)
    trades_df = (
        pd.DataFrame(trades)
        if trades
        else pd.DataFrame(
            columns=["entry_time", "exit_time", "entry_price", "exit_price", "pnl", "side"]
        )
    )

    return equity_curve, trades_df


def calculate_metrics(
    equity_curve: pd.Series,
    trades: pd.DataFrame,
    initial_cash: float = 100000.0,
    timeframe: str = "1D",
) -> BacktestMetrics:
    """Calculate comprehensive performance metrics from equity curve and trade log."""
    if equity_curve.empty:
        return _empty_metrics()

    # --- Return metrics ---
    total_return = (equity_curve.iloc[-1] - initial_cash) / initial_cash

    days = max((equity_curve.index[-1] - equity_curve.index[0]).days, 1)
    annualized_return = float((1.0 + total_return) ** (365.0 / days) - 1.0)

    # --- Risk-adjusted metrics ---
    per_bar_returns = equity_curve.pct_change().dropna()
    bars_per_year = _BARS_PER_YEAR.get(timeframe, 252.0)
    ann_factor = np.sqrt(bars_per_year)

    std = float(per_bar_returns.std())
    sharpe_ratio = float(per_bar_returns.mean() / std) * ann_factor if std > 1e-10 else 0.0

    downside = per_bar_returns[per_bar_returns < 0]
    down_std = float(downside.std()) if len(downside) > 1 else 0.0
    sortino_ratio = (
        float(per_bar_returns.mean() / down_std) * ann_factor if down_std > 1e-10 else 0.0
    )

    # --- Drawdown ---
    running_max = equity_curve.cummax()
    drawdown_series = (equity_curve - running_max) / running_max
    max_drawdown = float(abs(drawdown_series.min()))

    # Longest consecutive drawdown period (bar count)
    in_dd = drawdown_series < 0
    current_run = 0
    max_dd_duration = 0
    for flag in in_dd:
        current_run = current_run + 1 if flag else 0
        max_dd_duration = max(max_dd_duration, current_run)

    # --- Trade metrics ---
    if len(trades) == 0 or "pnl" not in trades.columns:
        return BacktestMetrics(
            total_return=round(total_return, 6),
            annualized_return=round(annualized_return, 6),
            sharpe_ratio=round(sharpe_ratio, 4),
            sortino_ratio=round(sortino_ratio, 4),
            max_drawdown=round(max_drawdown, 6),
            max_drawdown_duration=max_dd_duration,
            win_rate=0.0,
            profit_factor=0.0,
            avg_win=0.0,
            avg_loss=0.0,
            largest_win=0.0,
            largest_loss=0.0,
            total_trades=0,
            avg_trade_duration=0.0,
            expectancy=0.0,
        )

    pnl = trades["pnl"]
    wins = pnl[pnl > 0]
    losses = pnl[pnl < 0]

    total_trades = len(pnl)
    win_rate = float(len(wins) / total_trades)
    avg_win = float(wins.mean()) if len(wins) > 0 else 0.0
    avg_loss = float(losses.mean()) if len(losses) > 0 else 0.0
    largest_win = float(wins.max()) if len(wins) > 0 else 0.0
    largest_loss = float(losses.min()) if len(losses) > 0 else 0.0

    loss_sum = float(losses.sum())
    profit_factor = float(wins.sum() / abs(loss_sum)) if loss_sum != 0 else 0.0
    expectancy = win_rate * avg_win + (1.0 - win_rate) * avg_loss

    avg_trade_duration = 0.0
    if "entry_time" in trades.columns and "exit_time" in trades.columns:
        duration_h = (
            pd.to_datetime(trades["exit_time"]) - pd.to_datetime(trades["entry_time"])
        ).dt.total_seconds() / 3600.0
        avg_trade_duration = float(duration_h.mean())

    return BacktestMetrics(
        total_return=round(total_return, 6),
        annualized_return=round(annualized_return, 6),
        sharpe_ratio=round(sharpe_ratio, 4),
        sortino_ratio=round(sortino_ratio, 4),
        max_drawdown=round(max_drawdown, 6),
        max_drawdown_duration=max_dd_duration,
        win_rate=round(win_rate, 4),
        profit_factor=round(profit_factor, 4),
        avg_win=round(avg_win, 2),
        avg_loss=round(avg_loss, 2),
        largest_win=round(largest_win, 2),
        largest_loss=round(largest_loss, 2),
        total_trades=total_trades,
        avg_trade_duration=round(avg_trade_duration, 2),
        expectancy=round(expectancy, 2),
    )


def monte_carlo_simulation(
    trades: pd.DataFrame,
    initial_cash: float = 100000.0,
    num_simulations: int = 1000,
    confidence_level: float = 95.0,
) -> MonteCarloResult:
    """
    Bootstrap Monte Carlo simulation over the trade sequence.

    Shuffles trade order (with replacement) to estimate:
    - Expected return distribution
    - Realistic drawdown range at the given confidence level
    - Risk of ruin (equity falling below 50% of initial capital)
    """
    if len(trades) == 0 or "pnl" not in trades.columns:
        return MonteCarloResult(
            median_return=0.0,
            drawdown_95th=0.0,
            risk_of_ruin=0.0,
            simulations=0,
        )

    pnl_array = trades["pnl"].to_numpy(dtype=float)
    rng = np.random.default_rng(seed=42)

    final_equities: list[float] = []
    max_drawdowns: list[float] = []
    ruin_events: list[bool] = []

    for _ in range(num_simulations):
        shuffled = rng.choice(pnl_array, size=len(pnl_array), replace=True)
        equity = np.empty(len(shuffled) + 1)
        equity[0] = initial_cash
        equity[1:] = initial_cash + np.cumsum(shuffled)

        running_max = np.maximum.accumulate(equity)
        drawdown = (equity - running_max) / running_max
        max_dd = float(abs(drawdown.min()))

        final_equities.append(float(equity[-1]))
        max_drawdowns.append(max_dd)
        ruin_events.append(bool(np.any(equity < initial_cash * 0.5)))

    fe = np.array(final_equities)
    md = np.array(max_drawdowns)

    risk_of_ruin = float(np.mean(ruin_events))
    median_return = float((np.median(fe) - initial_cash) / initial_cash)
    drawdown_pct = float(np.percentile(md, confidence_level))

    return MonteCarloResult(
        median_return=round(median_return, 6),
        drawdown_95th=round(drawdown_pct, 6),
        risk_of_ruin=round(risk_of_ruin, 4),
        simulations=num_simulations,
    )


def _empty_metrics() -> BacktestMetrics:
    return BacktestMetrics(
        total_return=0.0,
        annualized_return=0.0,
        sharpe_ratio=0.0,
        sortino_ratio=0.0,
        max_drawdown=0.0,
        max_drawdown_duration=0,
        win_rate=0.0,
        profit_factor=0.0,
        avg_win=0.0,
        avg_loss=0.0,
        largest_win=0.0,
        largest_loss=0.0,
        total_trades=0,
        avg_trade_duration=0.0,
        expectancy=0.0,
    )
