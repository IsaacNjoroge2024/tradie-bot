"""Loads Monte Carlo recommendation thresholds and defaults from config/monte_carlo.yml."""

import logging
from dataclasses import dataclass
from pathlib import Path

import yaml

logger = logging.getLogger(__name__)

# src/monte_carlo/thresholds.py -> parents[2] = backtesting-service/
_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config" / "monte_carlo.yml"


@dataclass(frozen=True)
class MonteCarloThresholds:
    """Recommendation thresholds and simulation defaults for the Monte Carlo engine."""

    # REJECTED-tier thresholds
    max_probability_of_ruin: float = 0.05
    max_drawdown_95_pct: float = 40.0
    min_probability_of_profit: float = 0.55

    # CAUTION-tier thresholds
    caution_probability_of_ruin: float = 0.02
    caution_drawdown_95_pct: float = 30.0
    max_var_95_pct: float = 25.0

    # Simulation defaults
    default_num_simulations: int = 10000
    default_ruin_threshold_pct: float = 50.0
    default_position_sizing: str = "fixed"

    # Stress test defaults
    stress_test_num_simulations: int = 5000


def _load() -> MonteCarloThresholds:
    """Read config/monte_carlo.yml, falling back to built-in defaults if missing/invalid."""
    if not _CONFIG_PATH.exists():
        logger.warning(
            "monte_carlo.yml not found at %s; using built-in threshold defaults", _CONFIG_PATH
        )
        return MonteCarloThresholds()

    try:
        with open(_CONFIG_PATH) as f:
            raw = yaml.safe_load(f) or {}

        # yaml.safe_load succeeds on syntactically valid YAML that is the wrong
        # shape for this config (e.g. a list or scalar document, or a value that
        # doesn't cast to float/int) — that's not a YAMLError/OSError, so it must
        # be validated here too, inside the same fallback path. THRESHOLDS is
        # built at import time, so an uncaught exception here would crash FastAPI
        # and CLI startup on a simple config typo.
        mc = raw.get("monte_carlo", {}) or {}
        default = mc.get("default", {}) or {}
        thresholds = mc.get("thresholds", {}) or {}
        stress_test = mc.get("stress_test", {}) or {}
        fallback = MonteCarloThresholds()

        return MonteCarloThresholds(
            max_probability_of_ruin=float(
                thresholds.get("max_probability_of_ruin", fallback.max_probability_of_ruin)
            ),
            max_drawdown_95_pct=float(
                thresholds.get("max_drawdown_95_pct", fallback.max_drawdown_95_pct)
            ),
            min_probability_of_profit=float(
                thresholds.get("min_probability_of_profit", fallback.min_probability_of_profit)
            ),
            caution_probability_of_ruin=float(
                thresholds.get("caution_probability_of_ruin", fallback.caution_probability_of_ruin)
            ),
            caution_drawdown_95_pct=float(
                thresholds.get("caution_drawdown_95_pct", fallback.caution_drawdown_95_pct)
            ),
            max_var_95_pct=float(thresholds.get("max_var_95_pct", fallback.max_var_95_pct)),
            default_num_simulations=int(
                default.get("num_simulations", fallback.default_num_simulations)
            ),
            default_ruin_threshold_pct=float(
                default.get("ruin_threshold_pct", fallback.default_ruin_threshold_pct)
            ),
            default_position_sizing=str(
                default.get("position_sizing", fallback.default_position_sizing)
            ),
            stress_test_num_simulations=int(
                stress_test.get("num_simulations", fallback.stress_test_num_simulations)
            ),
        )
    except (yaml.YAMLError, OSError, AttributeError, ValueError, TypeError) as exc:
        logger.warning(
            "Failed to load monte_carlo.yml (%s: %s); using built-in defaults",
            type(exc).__name__,
            exc,
        )
        return MonteCarloThresholds()


THRESHOLDS = _load()
