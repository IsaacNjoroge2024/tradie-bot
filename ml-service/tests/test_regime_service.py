from datetime import timedelta

import numpy as np
import pandas as pd

from src.regime.models import MarketRegime, RegimeState
from src.regime.regime_service import RegimeService


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
