from dataclasses import dataclass, field
from enum import Enum

import numpy as np


class MarketRegime(Enum):
    TRENDING_UP = "trending_up"
    TRENDING_DOWN = "trending_down"
    RANGING = "ranging"
    VOLATILE = "volatile"


@dataclass
class RegimeState:
    """Current market regime state"""

    regime: MarketRegime
    probability: float  # Confidence in current regime
    all_probabilities: dict[MarketRegime, float]
    duration: int  # Bars in current regime
    transition_matrix: np.ndarray  # Regime transition probabilities


@dataclass
class RegimeConfig:
    """Configuration for regime detection"""

    n_regimes: int = 4
    lookback_periods: int = 252  # Trading days for training
    update_frequency: int = 20  # Retrain every N bars
    features: list[str] = field(
        default_factory=lambda: [
            "returns_5",
            "returns_20",
            "volatility_5",
            "volatility_20",
            "volume_change",
            "range_pct",
            "ma_distance",
        ]
    )


@dataclass
class RegimeRecommendation:
    """Trading recommendations based on regime"""

    regime: MarketRegime
    position_size_multiplier: float  # 1.0 = normal, 0.5 = reduced
    preferred_strategies: list[str]
    avoid_strategies: list[str]
    stop_loss_multiplier: float  # Widen/tighten stops
    take_profit_multiplier: float
    max_positions: int
    notes: str
