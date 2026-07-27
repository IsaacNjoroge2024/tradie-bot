"""Loads regime detection thresholds and defaults from config/regime.yml."""

import logging
from dataclasses import dataclass
from pathlib import Path

import yaml

logger = logging.getLogger(__name__)

# src/regime/thresholds.py -> parents[2] = ml-service/
_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config" / "regime.yml"


@dataclass(frozen=True)
class RegimeThresholds:
    """Classification thresholds and recommendation multipliers for regime detection."""

    n_regimes: int = 4
    lookback_periods: int = 252
    update_frequency_hours: int = 4

    # Classification thresholds (applied to each state's mean *raw*, unscaled
    # feature values — not the standardized features the HMM is fit on).
    # trending_return is an *annualized* rate (bars_per_year / window applied
    # in HMMRegimeDetector._calculate_features), not a raw 5-bar % move, so it
    # is comparable across timeframes. 0.756 == 0.015 raw 5-bar return
    # annualized at 1D's 252 bars/year (0.015 * 252/5); this preserves the
    # exact 1D decision boundary this project's original calibration used.
    trending_return: float = 0.756
    volatile_volatility: float = 0.35

    # Recommendation multipliers per regime
    trending_up_position_multiplier: float = 1.0
    trending_up_stop_multiplier: float = 1.2
    trending_down_position_multiplier: float = 0.75
    trending_down_stop_multiplier: float = 1.0
    ranging_position_multiplier: float = 0.8
    ranging_stop_multiplier: float = 0.8
    volatile_position_multiplier: float = 0.5
    volatile_stop_multiplier: float = 1.5


def _load() -> RegimeThresholds:
    """Read config/regime.yml, falling back to built-in defaults if missing/invalid."""
    if not _CONFIG_PATH.exists():
        logger.warning(
            "regime.yml not found at %s; using built-in threshold defaults", _CONFIG_PATH
        )
        return RegimeThresholds()

    try:
        with open(_CONFIG_PATH) as f:
            raw = yaml.safe_load(f) or {}

        # yaml.safe_load succeeds on syntactically valid YAML that is the wrong
        # shape for this config (e.g. a list or scalar document, or a value that
        # doesn't cast to float/int) — that's not a YAMLError/OSError, so it must
        # be validated here too, inside the same fallback path. THRESHOLDS is
        # built at import time, so an uncaught exception here would crash FastAPI
        # startup on a simple config typo.
        cfg = raw.get("regime", {}) or {}
        thresholds = cfg.get("thresholds", {}) or {}
        recommendations = cfg.get("recommendations", {}) or {}
        trending_up = recommendations.get("trending_up", {}) or {}
        trending_down = recommendations.get("trending_down", {}) or {}
        ranging = recommendations.get("ranging", {}) or {}
        volatile = recommendations.get("volatile", {}) or {}
        fallback = RegimeThresholds()

        return RegimeThresholds(
            n_regimes=int(cfg.get("n_regimes", fallback.n_regimes)),
            lookback_periods=int(cfg.get("lookback_periods", fallback.lookback_periods)),
            update_frequency_hours=int(
                cfg.get("update_frequency_hours", fallback.update_frequency_hours)
            ),
            trending_return=float(thresholds.get("trending_return", fallback.trending_return)),
            volatile_volatility=float(
                thresholds.get("volatile_volatility", fallback.volatile_volatility)
            ),
            trending_up_position_multiplier=float(
                trending_up.get("position_multiplier", fallback.trending_up_position_multiplier)
            ),
            trending_up_stop_multiplier=float(
                trending_up.get("stop_multiplier", fallback.trending_up_stop_multiplier)
            ),
            trending_down_position_multiplier=float(
                trending_down.get("position_multiplier", fallback.trending_down_position_multiplier)
            ),
            trending_down_stop_multiplier=float(
                trending_down.get("stop_multiplier", fallback.trending_down_stop_multiplier)
            ),
            ranging_position_multiplier=float(
                ranging.get("position_multiplier", fallback.ranging_position_multiplier)
            ),
            ranging_stop_multiplier=float(
                ranging.get("stop_multiplier", fallback.ranging_stop_multiplier)
            ),
            volatile_position_multiplier=float(
                volatile.get("position_multiplier", fallback.volatile_position_multiplier)
            ),
            volatile_stop_multiplier=float(
                volatile.get("stop_multiplier", fallback.volatile_stop_multiplier)
            ),
        )
    except (yaml.YAMLError, OSError, AttributeError, ValueError, TypeError) as exc:
        logger.warning(
            "Failed to load regime.yml (%s: %s); using built-in defaults",
            type(exc).__name__,
            exc,
        )
        return RegimeThresholds()


THRESHOLDS = _load()
