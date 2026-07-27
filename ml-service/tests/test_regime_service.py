import threading
from datetime import datetime, timedelta
from unittest.mock import patch

import numpy as np
import pandas as pd

from src.regime.hmm_detector import HMMRegimeDetector
from src.regime.models import MarketRegime, RegimeState
from src.regime.regime_service import _MAX_CACHED_DETECTORS, RegimeService


def _make_data(n: int = 300, drift: float = 0.001, vol: float = 0.01) -> pd.DataFrame:
    np.random.seed(42)
    returns = np.random.normal(drift, vol, n)
    prices = 100 * np.cumprod(1 + returns)
    return pd.DataFrame(
        {
            "close": prices,
            "high": prices * 1.005,
            "low": prices * 0.995,
            "volume": np.random.uniform(1000000, 2000000, n),
        }
    )


class TestRegimeService:
    def test_get_regime_fits_and_caches_detector(self):
        service = RegimeService()
        data = _make_data()

        state = service.get_regime("AAPL", "1H", data)

        assert state.regime is not None
        assert "AAPL_1H" in service.detectors
        assert "AAPL_1H" in service.last_update

    def test_get_regime_does_not_refit_within_update_interval(self):
        service = RegimeService()
        data = _make_data()

        service.get_regime("AAPL", "1H", data)
        detector_after_first_call = service.detectors["AAPL_1H"]

        service.get_regime("AAPL", "1H", data)

        assert service.detectors["AAPL_1H"] is detector_after_first_call

    def test_get_regime_refits_after_update_interval_elapses(self):
        service = RegimeService()
        data = _make_data()

        service.get_regime("AAPL", "1H", data)
        detector_after_first_call = service.detectors["AAPL_1H"]

        # Simulate the update interval having elapsed
        service.last_update["AAPL_1H"] = service.last_update["AAPL_1H"] - timedelta(hours=999)

        service.get_regime("AAPL", "1H", data)

        assert service.detectors["AAPL_1H"] is not detector_after_first_call

    def test_get_regime_keys_detectors_by_symbol_and_timeframe(self):
        service = RegimeService()
        data = _make_data()

        service.get_regime("AAPL", "1H", data)
        service.get_regime("AAPL", "1D", data)
        service.get_regime("MSFT", "1H", data)

        assert set(service.detectors.keys()) == {"AAPL_1H", "AAPL_1D", "MSFT_1H"}

    def test_get_recommendation_matches_detected_regime(self):
        service = RegimeService()
        data = _make_data()

        recommendation = service.get_recommendation("AAPL", "1H", data)

        assert recommendation.position_size_multiplier > 0
        assert recommendation.regime in MarketRegime

    def test_get_regime_threads_timeframe_into_detector(self):
        """The detector built for a given key must be fit with that same
        timeframe, so its feature annualization matches (see hmm_detector's
        _bars_per_year — a detector fit with the wrong timeframe would
        silently miscalibrate returns_5/volatility_5)."""
        service = RegimeService()
        data = _make_data()

        service.get_regime("AAPL", "4H", data)

        assert service.detectors["AAPL_4H"].timeframe == "4H"

    def test_fit_detector_evicts_stalest_entry_when_over_capacity(self):
        """detectors/last_update are keyed by client-supplied symbol/timeframe
        with no allowlist — without a bound, distinct values would grow these
        dicts unboundedly for the life of the process."""
        service = RegimeService()
        data = _make_data()

        # Pre-fill to capacity with lightweight fake entries — avoids actually
        # fitting hundreds of real HMMs just to exercise the eviction path.
        base_time = datetime.now() - timedelta(days=1)
        for i in range(_MAX_CACHED_DETECTORS):
            key = f"FAKE{i}_1H"
            service.detectors[key] = HMMRegimeDetector()
            service.last_update[key] = base_time + timedelta(seconds=i)

        stalest_key = "FAKE0_1H"
        assert stalest_key in service.detectors

        service.get_regime("AAPL", "1H", data)  # real fit + eviction check

        assert len(service.detectors) == _MAX_CACHED_DETECTORS
        assert len(service.last_update) == _MAX_CACHED_DETECTORS
        assert stalest_key not in service.detectors
        assert "AAPL_1H" in service.detectors

    def test_get_regime_concurrent_calls_for_same_new_key_fit_only_once(self):
        """Regression test for the check-then-act race: concurrent requests
        for the same missing/stale key (real OS threads, matching how the API
        layer dispatches via asyncio.to_thread) must not trigger redundant,
        wasted HMM fits."""
        service = RegimeService()
        data = _make_data()

        fit_call_count = 0
        original_fit_detector = RegimeService._fit_detector

        def counting_fit_detector(self, key, price_data, timeframe):
            nonlocal fit_call_count
            fit_call_count += 1
            return original_fit_detector(self, key, price_data, timeframe)

        with patch.object(RegimeService, "_fit_detector", counting_fit_detector):
            threads = [
                threading.Thread(target=service.get_regime, args=("AAPL", "1H", data))
                for _ in range(8)
            ]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

        assert fit_call_count == 1


class TestShouldTrade:
    def _state(self, regime: MarketRegime, probability: float) -> RegimeState:
        return RegimeState(regime, probability, {regime: probability}, 5, np.eye(4))

    def test_all_timeframes_aligned_and_confident_trades(self):
        service = RegimeService()
        regimes = {
            "1H": self._state(MarketRegime.TRENDING_UP, 0.8),
            "4H": self._state(MarketRegime.TRENDING_UP, 0.8),
            "1D": self._state(MarketRegime.TRENDING_UP, 0.8),
        }

        result = service.should_trade(regimes)

        assert result["should_trade"] is True
        assert result["dominant_regime"] == MarketRegime.TRENDING_UP.value
        assert result["alignment"] == 1.0

    def test_volatile_dominant_and_aligned_avoids_trading(self):
        service = RegimeService()
        regimes = {
            "1H": self._state(MarketRegime.VOLATILE, 0.9),
            "4H": self._state(MarketRegime.VOLATILE, 0.9),
            "1D": self._state(MarketRegime.TRENDING_UP, 0.9),
        }

        result = service.should_trade(regimes)

        assert result["should_trade"] is False
        assert result["dominant_regime"] == MarketRegime.VOLATILE.value

    def test_mixed_regimes_trades_with_caution(self):
        service = RegimeService()
        regimes = {
            "1H": self._state(MarketRegime.TRENDING_UP, 0.5),
            "4H": self._state(MarketRegime.RANGING, 0.5),
            "1D": self._state(MarketRegime.TRENDING_DOWN, 0.5),
        }

        result = service.should_trade(regimes)

        assert result["should_trade"] is True
        assert "regimes" in result
        assert set(result["regimes"].keys()) == {"1H", "4H", "1D"}
