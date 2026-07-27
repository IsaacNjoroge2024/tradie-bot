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

    def test_duration_is_deterministic_and_idempotent_across_repeated_calls(self):
        """duration must be derived purely from the input window, not carried as
        mutable state across calls: two identical calls (e.g. a client polling
        before the next bar closes) must return the *same* duration, not an
        incrementing one."""
        data = create_ranging_data()

        detector = HMMRegimeDetector()
        detector.fit(data)

        first = detector.predict(data)
        second = detector.predict(data)

        assert first.regime == second.regime
        assert second.duration == first.duration

    def test_duration_reflects_trailing_run_length_within_the_window(self):
        data = create_trending_up_data()

        detector = HMMRegimeDetector()
        detector.fit(data)
        state = detector.predict(data)

        assert 1 <= state.duration <= len(detector._calculate_features(data))

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


class TestTimeframeAnnualization:
    """Covers the timeframe-aware annualization fix in _calculate_features.

    Before this fix, returns_5/20 and volatility_5/20 were annualized with a
    hardcoded sqrt(252) regardless of timeframe. Verified live against real
    seeded AAPL data: the VOLATILE threshold was crossed by 4.36% of 1D bars
    but only 0.43% of 1H bars for the *same symbol* — a ~10x discrepancy
    caused entirely by treating each 1H bar as if it were a full trading day,
    not by 1H genuinely being calmer.
    """

    def test_bars_per_year_known_timeframes(self):
        from src.regime.hmm_detector import _bars_per_year

        assert _bars_per_year("1D") == 252.0
        assert _bars_per_year("1H") == pytest.approx(252.0 * 6.5)
        assert _bars_per_year("4H") == pytest.approx(252.0 * 1.625)

    def test_bars_per_year_unknown_timeframe_falls_back_to_daily(self):
        from src.regime.hmm_detector import _bars_per_year

        assert _bars_per_year("3D") == 252.0

    def test_bars_per_year_is_case_insensitive(self):
        from src.regime.hmm_detector import _bars_per_year

        assert _bars_per_year("1h") == _bars_per_year("1H")

    def test_default_timeframe_is_1d(self):
        assert HMMRegimeDetector().timeframe == "1D"

    def test_1d_behavior_unchanged_from_hardcoded_sqrt_252(self):
        """Regression guard: the default (1D) detector must reproduce exactly
        what the old hardcoded `* np.sqrt(252)` computed."""
        data = create_trending_up_data()
        detector = HMMRegimeDetector()
        features = detector._calculate_features(data)

        raw_returns_1 = data["close"].pct_change(1)
        expected_volatility_5 = (raw_returns_1.rolling(5).std() * np.sqrt(252)).dropna()

        np.testing.assert_allclose(
            features[:, 2][-10:], expected_volatility_5.values[-10:], rtol=1e-9
        )

    def test_1h_scales_features_relative_to_1d_by_bars_per_year_ratio(self):
        """Same raw price series, different timeframe label: returns_5 must scale
        linearly by the bars-per-year ratio; volatility_5 by its square root."""
        data = create_trending_up_data()

        features_1d = HMMRegimeDetector(timeframe="1D")._calculate_features(data)
        features_1h = HMMRegimeDetector(timeframe="1H")._calculate_features(data)

        returns_5_ratio = features_1h[:, 0] / features_1d[:, 0]
        volatility_5_ratio = features_1h[:, 2] / features_1d[:, 2]

        np.testing.assert_allclose(returns_5_ratio, 6.5, rtol=1e-6)
        np.testing.assert_allclose(volatility_5_ratio, np.sqrt(6.5), rtol=1e-6)

    def test_matched_annualized_volatility_produces_comparable_features_across_timeframes(
        self,
    ):
        """The actual bug this fixes: 1H bars carrying the same *true* annualized
        volatility as some 1D bars must now produce comparable volatility_5
        features, so a single threshold treats both fairly."""
        n = 300
        np.random.seed(11)
        daily_returns = np.random.normal(0, 0.03, n)
        daily_prices = 100 * np.cumprod(1 + daily_returns)
        daily_df = pd.DataFrame(
            {
                "close": daily_prices,
                "high": daily_prices * 1.02,
                "low": daily_prices * 0.98,
                "volume": np.random.uniform(2000000, 5000000, n),
            }
        )

        # Same annualized volatility as the daily series above, expressed as
        # hourly bars: per-bar stdev scales down by sqrt(bars_per_year ratio).
        np.random.seed(11)
        hourly_returns = np.random.normal(0, 0.03 / np.sqrt(6.5), n)
        hourly_prices = 100 * np.cumprod(1 + hourly_returns)
        hourly_df = pd.DataFrame(
            {
                "close": hourly_prices,
                "high": hourly_prices * 1.005,
                "low": hourly_prices * 0.995,
                "volume": np.random.uniform(2000000, 5000000, n),
            }
        )

        features_1d = HMMRegimeDetector(timeframe="1D")._calculate_features(daily_df)
        features_1h = HMMRegimeDetector(timeframe="1H")._calculate_features(hourly_df)

        assert features_1h[:, 2].mean() == pytest.approx(features_1d[:, 2].mean(), rel=0.05)

    def test_fit_and_predict_with_explicit_timeframe(self):
        data = create_trending_up_data()
        detector = HMMRegimeDetector(timeframe="4H")
        detector.fit(data)

        assert detector.timeframe == "4H"
        state = detector.predict(data)
        assert state.regime is not None


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
