import logging
from typing import List, Tuple

import numpy as np
from numpy.random import Generator, PCG64
from joblib import Parallel, delayed

from .models import MonteCarloResult, SimulationConfig, Trade
from .thresholds import THRESHOLDS

logger = logging.getLogger(__name__)

# Minimum trade count for a statistically meaningful simulation. Shared with
# stress_tests.py (to avoid a scenario shrinking a trade list below this floor)
# and the API/router layers that gate access to this simulator.
MIN_TRADES_REQUIRED = 30


class MonteCarloSimulator:
    """
    Monte Carlo simulator for trading strategy validation.

    Runs thousands of simulations by reshuffling trade sequences
    to reveal realistic risk distributions that single backtests hide.

    Note: with the default config (skip_trade_pct=0, position_sizing="fixed"),
    shuffling is a pure permutation, so every simulation's *final* balance is
    identical (only the equity path/drawdown differs) — VaR, CVaR and the
    final-balance percentiles are therefore degenerate in that configuration.
    They only carry distributional signal once skip_trade_pct > 0 or
    position_sizing="compounding" is used. Drawdown percentiles and streak
    metrics remain meaningful in all configurations.
    """

    def __init__(self, config: SimulationConfig):
        self.config = config

    def run(self, trades: List[Trade], n_jobs: int = -1) -> MonteCarloResult:
        """
        Run Monte Carlo simulation on historical trades.

        Args:
            trades: List of historical trades
            n_jobs: Number of parallel jobs (-1 = all cores)

        Returns:
            MonteCarloResult with all statistics
        """
        if len(trades) < MIN_TRADES_REQUIRED:
            raise ValueError(
                f"Need at least {MIN_TRADES_REQUIRED} trades for meaningful Monte Carlo analysis"
            )

        if self.config.skip_trade_pct <= 0 and self.config.position_sizing == "fixed":
            logger.info(
                "Running with skip_trade_pct=0 and position_sizing='fixed': permutation "
                "preserves total P&L, so final-balance VaR/CVaR/percentiles will be "
                "degenerate (identical across simulations). Drawdown and streak metrics "
                "remain meaningful."
            )

        pnl_values = np.array([t.pnl for t in trades], dtype=float)

        # Run simulations in parallel
        results = Parallel(n_jobs=n_jobs)(
            delayed(self._run_single_simulation)(pnl_values, i)
            for i in range(self.config.num_simulations)
        )

        # Unpack results
        final_balances = np.array([r[0] for r in results], dtype=float)
        max_drawdowns = np.array([r[1] for r in results], dtype=float)
        equity_curves = [r[2] for r in results]
        ruin_flags = np.array([r[3] for r in results], dtype=bool)
        losing_streaks = np.array([r[4] for r in results], dtype=int)

        # Calculate statistics
        return self._calculate_statistics(
            trades, final_balances, max_drawdowns, equity_curves, ruin_flags, losing_streaks
        )

    def _run_single_simulation(
        self, pnl_values: np.ndarray, sim_index: int
    ) -> Tuple[float, float, List[float], bool, int]:
        """Run a single simulation with shuffled trades"""

        # Create local RNG for thread safety
        seed = self.config.random_seed + sim_index if self.config.random_seed is not None else None
        local_rng = Generator(PCG64(seed))

        # Shuffle trade order
        shuffled_pnl = local_rng.permutation(pnl_values)

        # Optionally skip some trades (stress test)
        if self.config.skip_trade_pct > 0:
            n_trades = len(shuffled_pnl)
            n_skip = int(n_trades * self.config.skip_trade_pct / 100)
            if n_skip > 0 and n_skip < n_trades:
                skip_indices = local_rng.choice(n_trades, n_skip, replace=False)
                shuffled_pnl = np.delete(shuffled_pnl, skip_indices)

        # Simulate equity curve
        balance = float(self.config.initial_balance)
        equity_curve = [balance]
        peak = balance
        max_drawdown = 0.0
        hit_ruin = False

        # Track losing streak
        current_losing_streak = 0
        max_losing_streak = 0

        for pnl in shuffled_pnl:
            # Apply position sizing if compounding
            if self.config.position_sizing == "compounding":
                # Scale P&L based on current balance vs initial
                scale_factor = (
                    balance / self.config.initial_balance
                    if self.config.initial_balance > 0
                    else 1.0
                )
                adjusted_pnl = pnl * scale_factor
            else:
                adjusted_pnl = pnl

            balance += adjusted_pnl
            equity_curve.append(balance)

            # Track peak and drawdown
            if balance > peak:
                peak = balance

            drawdown_pct = ((peak - balance) / peak) * 100.0 if peak > 0 else 0.0
            max_drawdown = max(max_drawdown, drawdown_pct)

            # Check for ruin
            if drawdown_pct >= self.config.ruin_threshold_pct:
                hit_ruin = True

            # Track losing streak
            if pnl < 0:
                current_losing_streak += 1
                max_losing_streak = max(max_losing_streak, current_losing_streak)
            else:
                current_losing_streak = 0

        return (balance, max_drawdown, equity_curve, hit_ruin, max_losing_streak)

    def _calculate_statistics(
        self,
        trades: List[Trade],
        final_balances: np.ndarray,
        max_drawdowns: np.ndarray,
        equity_curves: List[List[float]],
        ruin_flags: np.ndarray,
        losing_streaks: np.ndarray,
    ) -> MonteCarloResult:
        """Calculate all statistics from simulation results"""

        initial = float(self.config.initial_balance)

        # Sort for percentile curves
        sorted_indices = np.argsort(final_balances)

        # Get representative curves
        worst_idx = sorted_indices[0]
        p5_idx = sorted_indices[int(len(sorted_indices) * 0.05)]
        median_idx = sorted_indices[len(sorted_indices) // 2]
        p95_idx = sorted_indices[int(len(sorted_indices) * 0.95)]
        best_idx = sorted_indices[-1]

        # Calculate VaR and CVaR
        returns = (final_balances - initial) / initial
        p5_bal = float(np.percentile(final_balances, 5))
        p1_bal = float(np.percentile(final_balances, 1))
        var_95 = initial - p5_bal
        var_99 = initial - p1_bal

        # CVaR (Expected Shortfall) = mean of losses beyond VaR
        worst_5_pct = final_balances[final_balances <= p5_bal]
        cvar_95 = initial - float(np.mean(worst_5_pct)) if len(worst_5_pct) > 0 else var_95

        # Sharpe ratio estimate. Guard with an epsilon (not a bare > 0) since
        # in the degenerate case (skip_trade_pct=0, position_sizing="fixed")
        # every simulation's final balance is mathematically identical and
        # std_returns should be exactly 0 — but floating-point summation order
        # differs per permutation, leaving a near-zero (e.g. 1e-16) noise floor
        # that would otherwise blow mean/std up into a meaningless huge number.
        std_returns = float(np.std(returns))
        if std_returns > 1e-10:
            expected_sharpe = float(np.mean(returns) / std_returns)
        else:
            expected_sharpe = 0.0

        # Generate recommendation
        prob_ruin = float(np.mean(ruin_flags))
        p95_dd = float(np.percentile(max_drawdowns, 95))
        prob_profit = float(np.mean(final_balances > initial))
        var_95_pct = (var_95 / initial) * 100.0 if initial > 0 else 0.0

        recommendation, reasons = self._generate_recommendation(
            probability_of_ruin=prob_ruin,
            percentile_95_drawdown=p95_dd,
            probability_of_profit=prob_profit,
            var_95_pct=var_95_pct,
        )

        return MonteCarloResult(
            config=self.config,
            num_trades=len(trades),
            probability_of_profit=prob_profit,
            probability_of_ruin=prob_ruin,
            median_final_balance=float(np.median(final_balances)),
            mean_final_balance=float(np.mean(final_balances)),
            std_final_balance=float(np.std(final_balances)),
            percentile_5_balance=p5_bal,
            percentile_25_balance=float(np.percentile(final_balances, 25)),
            percentile_75_balance=float(np.percentile(final_balances, 75)),
            percentile_95_balance=float(np.percentile(final_balances, 95)),
            min_final_balance=float(np.min(final_balances)),
            max_final_balance=float(np.max(final_balances)),
            median_max_drawdown=float(np.median(max_drawdowns)),
            mean_max_drawdown=float(np.mean(max_drawdowns)),
            percentile_95_max_drawdown=p95_dd,
            percentile_99_max_drawdown=float(np.percentile(max_drawdowns, 99)),
            var_95=float(var_95),
            var_99=float(var_99),
            cvar_95=float(cvar_95),
            expected_sharpe=expected_sharpe,
            worst_losing_streak=int(np.max(losing_streaks)),
            median_losing_streak=int(np.median(losing_streaks)),
            worst_winning_streak=0,  # Calculate separately if needed
            best_equity_curve=equity_curves[best_idx],
            worst_equity_curve=equity_curves[worst_idx],
            median_equity_curve=equity_curves[median_idx],
            percentile_5_curve=equity_curves[p5_idx],
            percentile_95_curve=equity_curves[p95_idx],
            recommendation=recommendation,
            rejection_reasons=reasons,
        )

    def _generate_recommendation(
        self,
        probability_of_ruin: float,
        percentile_95_drawdown: float,
        probability_of_profit: float,
        var_95_pct: float,
    ) -> Tuple[str, List[str]]:
        """Generate go/no-go recommendation based on risk metrics.

        Thresholds are sourced from config/monte_carlo.yml (see .thresholds.THRESHOLDS)
        so operators can tune go/no-go criteria without a code change.
        """

        reasons = []

        # Critical failures (REJECTED)
        if probability_of_ruin > THRESHOLDS.max_probability_of_ruin:
            reasons.append(
                f"Probability of ruin {probability_of_ruin:.1%} exceeds "
                f"{THRESHOLDS.max_probability_of_ruin:.0%} threshold"
            )

        if percentile_95_drawdown > THRESHOLDS.max_drawdown_95_pct:
            reasons.append(
                f"95th percentile drawdown {percentile_95_drawdown:.1f}% exceeds "
                f"{THRESHOLDS.max_drawdown_95_pct:.0f}% threshold"
            )

        if probability_of_profit < THRESHOLDS.min_probability_of_profit:
            reasons.append(
                f"Probability of profit {probability_of_profit:.1%} below "
                f"{THRESHOLDS.min_probability_of_profit:.0%} threshold"
            )

        if len(reasons) > 0:
            return "REJECTED", reasons

        # Warnings (CAUTION)
        warnings = []

        if probability_of_ruin > THRESHOLDS.caution_probability_of_ruin:
            warnings.append(
                f"Probability of ruin {probability_of_ruin:.1%} above "
                f"{THRESHOLDS.caution_probability_of_ruin:.0%} (acceptable but elevated)"
            )

        if percentile_95_drawdown > THRESHOLDS.caution_drawdown_95_pct:
            warnings.append(
                f"95th percentile drawdown {percentile_95_drawdown:.1f}% above "
                f"{THRESHOLDS.caution_drawdown_95_pct:.0f}%"
            )

        if var_95_pct > THRESHOLDS.max_var_95_pct:
            warnings.append(f"VaR 95% is {var_95_pct:.1f}% of account")

        if len(warnings) > 0:
            return "CAUTION", warnings

        return "APPROVED", []
