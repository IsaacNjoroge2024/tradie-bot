import numpy as np
import pandas as pd
import pytest

from src.regime.hmm_detector import HMMRegimeDetector
from src.regime.models import MarketRegime, RegimeConfig


def create_trending_up_data(n: int = 300) -> pd.DataFrame:
    """Create synthetic uptrending data"""
    np.random.seed(42)

    returns = np.random.normal(0.001, 0.01, n)  # Positive drift
    prices = 100 * np.cumprod(1 + returns)

    return pd.DataFrame(
        {
            "close": prices,
            "high": prices * 1.005,
            "low": prices * 0.995,
            "volume": np.random.uniform(1000000, 2000000, n),
        }
    )


def create_trending_down_data(n: int = 300) -> pd.DataFrame:
    """Create synthetic downtrending data"""
    np.random.seed(42)

    returns = np.random.normal(-0.001, 0.01, n)  # Negative drift
    prices = 100 * np.cumprod(1 + returns)

    return pd.DataFrame(
        {
            "close": prices,
            "high": prices * 1.005,
            "low": prices * 0.995,
            "volume": np.random.uniform(1000000, 2000000, n),
        }
    )


def create_ranging_data(n: int = 300) -> pd.DataFrame:
    """Create synthetic range-bound data"""
    np.random.seed(42)

    returns = np.random.normal(0, 0.004, n)  # No drift, low volatility
    prices = 100 * np.cumprod(1 + returns)

    return pd.DataFrame(
        {
            "close": prices,
            "high": prices * 1.003,
            "low": prices * 0.997,
            "volume": np.random.uniform(800000, 1200000, n),
        }
    )


def create_volatile_data(n: int = 300) -> pd.DataFrame:
    """Create synthetic volatile data"""
    np.random.seed(42)

    returns = np.random.normal(0, 0.03, n)  # High volatility
    prices = 100 * np.cumprod(1 + returns)

    return pd.DataFrame(
        {
            "close": prices,
            "high": prices * 1.02,
            "low": prices * 0.98,
            "volume": np.random.uniform(2000000, 5000000, n),
        }
    )


class TestHMMRegimeDetector:
    def test_fit_and_predict(self):
        """Test basic fit and predict"""
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)

        state = detector.predict(data)

        assert state.regime is not None
        assert 0 <= state.probability <= 1
        assert len(state.all_probabilities) == 4

    def test_fit_insufficient_data_raises(self):
        """Fitting on too few bars (all-NaN after feature rolling windows) should fail clearly"""
        data = create_trending_up_data(n=10)

        detector = HMMRegimeDetector()
        with pytest.raises(ValueError):
            detector.fit(data)

    def test_predict_before_fit_raises(self):
        detector = HMMRegimeDetector()
        with pytest.raises(ValueError):
            detector.predict(create_trending_up_data())

    def test_trending_up_detection(self):
        """Test detection of trending-up regime"""
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert state.regime in [MarketRegime.TRENDING_UP, MarketRegime.RANGING]

    def test_trending_down_detection(self):
        """Test detection of trending-down regime"""
        data = create_trending_down_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert state.regime in [MarketRegime.TRENDING_DOWN, MarketRegime.RANGING]

    def test_ranging_detection(self):
        """Test detection of range-bound regime"""
        data = create_ranging_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert state.regime == MarketRegime.RANGING

    def test_volatile_detection(self):
        """Test detection of volatile regime"""
        data = create_volatile_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        # Should have elevated probability of volatile regime
        assert state.all_probabilities[MarketRegime.VOLATILE] > 0.2

    def test_all_probabilities_cover_all_regimes(self):
        """All 4 regimes must always be present, even if no HMM state mapped to one"""
        data = create_volatile_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert set(state.all_probabilities.keys()) == set(MarketRegime)

    def test_all_probabilities_sum_to_one(self):
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert sum(state.all_probabilities.values()) == pytest.approx(1.0, abs=1e-6)

    def test_transition_matrix_is_valid(self):
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert state.transition_matrix.shape == (4, 4)
        np.testing.assert_allclose(state.transition_matrix.sum(axis=1), np.ones(4), atol=1e-6)

    def test_duration_increments_while_regime_is_stable(self):
        data = create_ranging_data()

        detector = HMMRegimeDetector()
        detector.fit(data)

        first = detector.predict(data)
        second = detector.predict(data)

        assert first.regime == second.regime
        assert second.duration == first.duration + 1

    def test_recommendation_generation(self):
        """Test that recommendations are generated correctly"""
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)
        recommendation = detector.get_recommendation(state)

        assert recommendation.position_size_multiplier > 0
        assert len(recommendation.preferred_strategies) > 0
        assert recommendation.notes is not None

    @pytest.mark.parametrize(
        "regime",
        [
            MarketRegime.TRENDING_UP,
            MarketRegime.TRENDING_DOWN,
            MarketRegime.RANGING,
            MarketRegime.VOLATILE,
        ],
    )
    def test_recommendation_for_every_regime(self, regime):
        """Every regime must map to a distinct, sane recommendation"""
        from src.regime.models import RegimeState

        detector = HMMRegimeDetector()
        state = RegimeState(
            regime=regime,
            probability=0.9,
            all_probabilities={r: 0.9 if r == regime else 0.0333 for r in MarketRegime},
            duration=5,
            transition_matrix=np.eye(4),
        )

        recommendation = detector.get_recommendation(state)

        assert recommendation.regime == regime
        assert recommendation.position_size_multiplier > 0
        assert recommendation.stop_loss_multiplier > 0
        assert recommendation.take_profit_multiplier > 0
        assert recommendation.max_positions > 0

    def test_volatile_regime_reduces_position_size_most(self):
        """VOLATILE must carry the most conservative position sizing of all 4 regimes"""
        from src.regime.models import RegimeState

        detector = HMMRegimeDetector()
        multipliers = {}
        for regime in MarketRegime:
            state = RegimeState(regime, 0.9, {regime: 0.9}, 1, np.eye(4))
            multipliers[regime] = detector.get_recommendation(state).position_size_multiplier

        assert multipliers[MarketRegime.VOLATILE] == min(multipliers.values())


class TestRegimeConfig:
    def test_defaults(self):
        config = RegimeConfig()

        assert config.n_regimes == 4
        assert config.features == [
            "returns_5",
            "returns_20",
            "volatility_5",
            "volatility_20",
            "volume_change",
            "range_pct",
            "ma_distance",
        ]

    def test_default_features_list_not_shared_across_instances(self):
        """Each RegimeConfig() must get its own features list, not a shared mutable default"""
        a = RegimeConfig()
        b = RegimeConfig()

        a.features.append("extra")

        assert "extra" not in b.features
