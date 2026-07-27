import logging
import threading
from datetime import datetime, timedelta

import pandas as pd

from .hmm_detector import HMMRegimeDetector
from .models import MarketRegime, RegimeConfig, RegimeRecommendation, RegimeState
from .thresholds import THRESHOLDS

logger = logging.getLogger(__name__)

# Caps the detectors/last_update dicts, which are keyed by client-supplied
# "{symbol}_{timeframe}" strings with no allowlist — without a bound, a caller
# sending many distinct values would grow these dicts unboundedly for the
# life of the process. 500 matches strategy-engine's own Caffeine cache size
# for a comparable purpose (marketStatus/accountInfo, see CacheConfig).
_MAX_CACHED_DETECTORS = 500


class RegimeService:
    """
    Service for managing regime detection across multiple symbols/timeframes.
    """

    def __init__(self):
        self.detectors: dict[str, HMMRegimeDetector] = {}
        self.last_update: dict[str, datetime] = {}
        self.update_interval = timedelta(hours=THRESHOLDS.update_frequency_hours)
        # Guards fit-or-reuse below. Real OS threads (via asyncio.to_thread in
        # the API layer), not coroutines on one event loop, can call get_regime
        # concurrently for the same key — an unsynchronized check-then-act
        # would let two threads both see a stale/missing entry and redundantly
        # fit the same detector at once.
        self._fit_lock = threading.Lock()

    def get_regime(self, symbol: str, timeframe: str, price_data: pd.DataFrame) -> RegimeState:
        """Get current regime for a symbol"""

        key = f"{symbol}_{timeframe}"

        if key not in self.detectors or self._needs_update(key):
            with self._fit_lock:
                # Re-check: another thread may have fit this key while we
                # were waiting for the lock.
                if key not in self.detectors or self._needs_update(key):
                    self._fit_detector(key, price_data, timeframe)

        return self.detectors[key].predict(price_data)

    def get_recommendation(
        self, symbol: str, timeframe: str, price_data: pd.DataFrame
    ) -> RegimeRecommendation:
        """Get trading recommendation based on regime"""

        state = self.get_regime(symbol, timeframe, price_data)
        key = f"{symbol}_{timeframe}"

        return self.detectors[key].get_recommendation(state)

    def _fit_detector(self, key: str, price_data: pd.DataFrame, timeframe: str):
        """Fit or refit a detector. Always called while holding self._fit_lock."""

        config = RegimeConfig(
            n_regimes=THRESHOLDS.n_regimes, lookback_periods=THRESHOLDS.lookback_periods
        )
        detector = HMMRegimeDetector(config, timeframe)
        detector.fit(price_data)

        self.detectors[key] = detector
        self.last_update[key] = datetime.now()

        if len(self.detectors) > _MAX_CACHED_DETECTORS:
            stalest_key = min(self.last_update, key=self.last_update.get)
            del self.detectors[stalest_key]
            del self.last_update[stalest_key]
            logger.info(
                "Evicted regime detector for %s (cache exceeded %d entries)",
                stalest_key,
                _MAX_CACHED_DETECTORS,
            )

        logger.info("Fitted regime detector for %s", key)

    def _needs_update(self, key: str) -> bool:
        """Check if detector needs retraining"""

        if key not in self.last_update:
            return True

        return datetime.now() - self.last_update[key] > self.update_interval

    def get_multi_timeframe_regime(
        self,
        symbol: str,
        price_data_1h: pd.DataFrame,
        price_data_4h: pd.DataFrame,
        price_data_1d: pd.DataFrame,
    ) -> dict[str, RegimeState]:
        """Get regime across multiple timeframes"""

        return {
            "1H": self.get_regime(symbol, "1H", price_data_1h),
            "4H": self.get_regime(symbol, "4H", price_data_4h),
            "1D": self.get_regime(symbol, "1D", price_data_1d),
        }

    def should_trade(self, regimes: dict[str, RegimeState]) -> dict:
        """Determine if trading conditions are favorable"""

        regime_counts: dict[MarketRegime, int] = {}
        for state in regimes.values():
            regime_counts[state.regime] = regime_counts.get(state.regime, 0) + 1

        dominant_regime = max(regime_counts, key=regime_counts.get)
        alignment = regime_counts[dominant_regime] / len(regimes)

        avg_confidence = sum(s.probability for s in regimes.values()) / len(regimes)

        if dominant_regime == MarketRegime.VOLATILE and alignment > 0.5:
            should_trade = False
            reason = "High volatility across timeframes - avoid trading"
        elif alignment >= 0.66 and avg_confidence > 0.6:
            should_trade = True
            reason = f"Good alignment ({alignment:.0%}) in {dominant_regime.value} regime"
        else:
            should_trade = True  # Neutral - trade with caution
            reason = "Mixed regimes - trade with reduced size"

        return {
            "should_trade": should_trade,
            "reason": reason,
            "dominant_regime": dominant_regime.value,
            "alignment": alignment,
            "avg_confidence": avg_confidence,
            "regimes": {tf: s.regime.value for tf, s in regimes.items()},
        }
