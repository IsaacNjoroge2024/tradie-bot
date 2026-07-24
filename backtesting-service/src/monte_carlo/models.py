from dataclasses import dataclass
from typing import List, Optional


@dataclass
class Trade:
    """Single trade record for simulation"""

    trade_id: str
    symbol: str
    side: str  # BUY or SELL
    entry_price: float
    exit_price: float
    quantity: float
    pnl: float  # Absolute P&L in dollars
    pnl_pct: float  # Percentage return
    hold_time_minutes: int
    strategy: str
    timestamp: str


@dataclass
class SimulationConfig:
    """Configuration for Monte Carlo simulation"""

    initial_balance: float = 10000.0
    num_simulations: int = 10000
    ruin_threshold_pct: float = 50.0  # 50% drawdown = ruin
    skip_trade_pct: float = 0.0  # % of trades to randomly skip
    position_sizing: str = "fixed"  # fixed, compounding
    risk_per_trade_pct: float = 2.0
    random_seed: Optional[int] = None


@dataclass
class MonteCarloResult:
    """Complete results from Monte Carlo simulation"""

    # Configuration
    config: SimulationConfig
    num_trades: int

    # Probability Metrics
    probability_of_profit: float  # % of sims that ended profitable
    probability_of_ruin: float  # % of sims that hit ruin threshold

    # Balance Distribution
    median_final_balance: float
    mean_final_balance: float
    std_final_balance: float
    percentile_5_balance: float  # Worst 5%
    percentile_25_balance: float
    percentile_75_balance: float
    percentile_95_balance: float  # Best 5%
    min_final_balance: float
    max_final_balance: float

    # Drawdown Distribution
    median_max_drawdown: float
    mean_max_drawdown: float
    percentile_95_max_drawdown: float  # Worst case drawdown
    percentile_99_max_drawdown: float  # Extreme worst case

    # Risk Metrics
    var_95: float  # Value at Risk 95%
    var_99: float  # Value at Risk 99%
    cvar_95: float  # Conditional VaR (Expected Shortfall)
    expected_sharpe: float

    # Streak Analysis
    worst_losing_streak: int
    median_losing_streak: int
    worst_winning_streak: int

    # Equity Curves (for visualization)
    best_equity_curve: List[float]
    worst_equity_curve: List[float]
    median_equity_curve: List[float]
    percentile_5_curve: List[float]
    percentile_95_curve: List[float]

    # Recommendation
    recommendation: str  # APPROVED, CAUTION, REJECTED
    # Reasons behind `recommendation` — populated for both CAUTION (warnings)
    # and REJECTED (hard failures), so this is deliberately not named
    # "rejection_reasons": a CAUTION result is not a rejection.
    recommendation_reasons: List[str]
