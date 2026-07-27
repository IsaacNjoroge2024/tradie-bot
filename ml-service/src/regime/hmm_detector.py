import logging

import numpy as np
import pandas as pd
from hmmlearn import hmm
from sklearn.preprocessing import StandardScaler

from .models import MarketRegime, RegimeConfig, RegimeRecommendation, RegimeState
from .thresholds import THRESHOLDS

logger = logging.getLogger(__name__)


class HMMRegimeDetector:
    """
    Hidden Markov Model for market regime detection.

    Detects 4 regimes based on returns, volatility, and volume patterns:
    - TRENDING_UP: Positive returns, moderate volatility
    - TRENDING_DOWN: Negative returns, elevated volatility
    - RANGING: Near-zero returns, low volatility
    - VOLATILE: High volatility, erratic returns
    """

    def __init__(self, config: RegimeConfig | None = None):
        self.config = config or RegimeConfig()
        self.model: hmm.GaussianHMM | None = None
        self.scaler = StandardScaler()
        self.regime_mapping: dict[int, MarketRegime] = {}
        self.is_fitted = False
        self.current_regime_duration = 0
        self.last_regime: MarketRegime | None = None

    def fit(self, price_data: pd.DataFrame) -> "HMMRegimeDetector":
        """
        Fit HMM on historical price data.

        Args:
            price_data: DataFrame with columns ['close', 'high', 'low', 'volume']

        Returns:
            self for method chaining
        """
        features = self._calculate_features(price_data)
        if len(features) < self.config.n_regimes:
            raise ValueError(
                f"Not enough data to fit HMM: need at least {self.config.n_regimes} "
                f"feature rows, got {len(features)}"
            )

        features_scaled = self.scaler.fit_transform(features)

        self.model = hmm.GaussianHMM(
            n_components=self.config.n_regimes,
            # "diag" rather than "full": with 7 features x 4 states, full covariance
            # needs ~112 free parameters against a ~250-sample lookback window, which
            # is prone to near-singular matrices and unstable/non-converging fits.
            # Diagonal covariance (~28 parameters) is far more stable at this
            # sample size while still capturing per-feature variance.
            covariance_type="diag",
            n_iter=1000,
            random_state=42,
            init_params="stmc",  # Initialize all parameters
        )

        self.model.fit(features_scaled)

        # Map HMM states to regime names based on characteristics. Uses the
        # *raw* (unscaled) feature means per state — see _map_regimes_to_states.
        self._map_regimes_to_states(features, features_scaled)

        self.is_fitted = True
        logger.info(
            "HMM fitted with %d regimes on %d samples", self.config.n_regimes, len(features)
        )

        return self

    def _calculate_features(self, data: pd.DataFrame) -> np.ndarray:
        """Calculate features for regime detection"""

        df = data.copy()

        # Returns (multiple windows)
        df["returns_1"] = df["close"].pct_change(1)
        df["returns_5"] = df["close"].pct_change(5)
        df["returns_20"] = df["close"].pct_change(20)

        # Volatility (realized volatility)
        df["volatility_5"] = df["returns_1"].rolling(5).std() * np.sqrt(252)
        df["volatility_20"] = df["returns_1"].rolling(20).std() * np.sqrt(252)

        # Volume change
        df["volume_ma"] = df["volume"].rolling(20).mean()
        df["volume_change"] = df["volume"] / df["volume_ma"] - 1

        # Range (high-low as % of close)
        df["range_pct"] = (df["high"] - df["low"]) / df["close"]

        # Trend strength (distance from 20-day MA)
        df["ma_20"] = df["close"].rolling(20).mean()
        df["ma_distance"] = (df["close"] - df["ma_20"]) / df["ma_20"]

        feature_cols = [
            "returns_5",
            "returns_20",
            "volatility_5",
            "volatility_20",
            "volume_change",
            "range_pct",
            "ma_distance",
        ]

        df = df.dropna()

        return df[feature_cols].values

    def _map_regimes_to_states(self, features_raw: np.ndarray, features_scaled: np.ndarray):
        """Map HMM states to semantic regime names based on characteristics.

        Classification runs on *raw* (unscaled) feature means, not the
        standardized ones the HMM is fit on. StandardScaler centers each
        feature on the fitting window's own mean, so a scaled returns_5 mean
        only reflects "above/below this window's average" — in a genuine
        uptrend, the below-average sub-periods still standardize to a
        negative z-score and would be mislabeled TRENDING_DOWN. Raw returns
        and annualized volatility carry real-world direction and magnitude,
        so thresholds against them (config: trending_return, volatile_volatility)
        classify correctly regardless of the window's own internal spread.
        """

        states = self.model.predict(features_scaled)

        state_means = {}
        for state in range(self.config.n_regimes):
            mask = states == state
            state_means[state] = (
                features_raw[mask].mean(axis=0) if mask.any() else features_raw.mean(axis=0)
            )

        # Features: [returns_5, returns_20, vol_5, vol_20, vol_change, range, ma_dist]
        state_characteristics = {}
        for state, means in state_means.items():
            returns_5 = means[0]
            volatility = means[2]

            if volatility > THRESHOLDS.volatile_volatility:
                state_characteristics[state] = MarketRegime.VOLATILE
            elif returns_5 > THRESHOLDS.trending_return:
                state_characteristics[state] = MarketRegime.TRENDING_UP
            elif returns_5 < -THRESHOLDS.trending_return:
                state_characteristics[state] = MarketRegime.TRENDING_DOWN
            else:
                state_characteristics[state] = MarketRegime.RANGING

        self.regime_mapping = state_characteristics

        mapped_regimes = set(state_characteristics.values())
        if len(mapped_regimes) < 4:
            logger.warning(
                "Only %d unique regimes detected out of 4. Some HMM states share a regime label.",
                len(mapped_regimes),
            )

    def predict(self, price_data: pd.DataFrame) -> RegimeState:
        """
        Predict current market regime.

        Args:
            price_data: Recent price data (at least 20 bars)

        Returns:
            RegimeState with current regime and probabilities
        """
        if not self.is_fitted:
            raise ValueError("Model must be fitted before prediction")

        features = self._calculate_features(price_data)
        if len(features) == 0:
            raise ValueError("Not enough data to calculate features")

        features_scaled = self.scaler.transform(features)

        # Get state sequence for the observation window
        _log_prob, state_sequence = self.model.decode(features_scaled)
        current_state = state_sequence[-1]

        # Get posterior probabilities
        posteriors = self.model.predict_proba(features_scaled)
        current_probs = posteriors[-1]

        current_regime = self.regime_mapping.get(current_state, MarketRegime.RANGING)

        # Track duration
        if current_regime == self.last_regime:
            self.current_regime_duration += 1
        else:
            self.current_regime_duration = 1
            self.last_regime = current_regime

        # Sum, not overwrite: multiple HMM states commonly collapse onto the
        # same regime label (see the warning in _map_regimes_to_states), and
        # a plain dict comprehension keyed by regime would silently drop all
        # but the last state's probability for that regime instead of
        # combining them. This also guarantees all 4 MarketRegime keys are
        # always present, even for a regime no state was mapped to (prob 0.0).
        all_probs = {regime: 0.0 for regime in MarketRegime}
        for i in range(self.config.n_regimes):
            regime = self.regime_mapping.get(i, MarketRegime.RANGING)
            all_probs[regime] += float(current_probs[i])

        return RegimeState(
            regime=current_regime,
            probability=float(current_probs[current_state]),
            all_probabilities=all_probs,
            duration=self.current_regime_duration,
            transition_matrix=self.model.transmat_,
        )

    def get_recommendation(self, regime_state: RegimeState) -> RegimeRecommendation:
        """Get trading recommendations based on current regime"""

        recommendations = {
            MarketRegime.TRENDING_UP: RegimeRecommendation(
                regime=MarketRegime.TRENDING_UP,
                position_size_multiplier=THRESHOLDS.trending_up_position_multiplier,
                preferred_strategies=["TREND_FOLLOWING", "MOMENTUM", "BREAKOUT"],
                avoid_strategies=["MEAN_REVERSION", "RANGE_TRADING"],
                stop_loss_multiplier=THRESHOLDS.trending_up_stop_multiplier,
                take_profit_multiplier=1.5,  # Let winners run
                max_positions=5,
                notes="Bullish trend detected. Favor long positions, use trailing stops.",
            ),
            MarketRegime.TRENDING_DOWN: RegimeRecommendation(
                regime=MarketRegime.TRENDING_DOWN,
                position_size_multiplier=THRESHOLDS.trending_down_position_multiplier,
                preferred_strategies=["TREND_FOLLOWING", "BREAKDOWN"],
                avoid_strategies=["BUY_THE_DIP", "MEAN_REVERSION"],
                stop_loss_multiplier=THRESHOLDS.trending_down_stop_multiplier,
                take_profit_multiplier=1.0,
                max_positions=3,
                notes="Bearish trend detected. Favor short positions or stay flat.",
            ),
            MarketRegime.RANGING: RegimeRecommendation(
                regime=MarketRegime.RANGING,
                position_size_multiplier=THRESHOLDS.ranging_position_multiplier,
                preferred_strategies=["MEAN_REVERSION", "RANGE_TRADING", "FVG"],
                avoid_strategies=["TREND_FOLLOWING", "BREAKOUT"],
                stop_loss_multiplier=THRESHOLDS.ranging_stop_multiplier,
                take_profit_multiplier=0.8,  # Take profits quicker
                max_positions=4,
                notes="Range-bound market. Trade support/resistance levels.",
            ),
            MarketRegime.VOLATILE: RegimeRecommendation(
                regime=MarketRegime.VOLATILE,
                position_size_multiplier=THRESHOLDS.volatile_position_multiplier,
                preferred_strategies=["VOLATILITY_BREAKOUT"],
                avoid_strategies=["SCALPING", "TIGHT_STOPS"],
                stop_loss_multiplier=THRESHOLDS.volatile_stop_multiplier,
                take_profit_multiplier=2.0,  # Larger targets
                max_positions=2,
                notes="High volatility detected. Reduce position size significantly.",
            ),
        }

        return recommendations.get(regime_state.regime, recommendations[MarketRegime.RANGING])
