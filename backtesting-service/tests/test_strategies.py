import pandas as pd
import pytest

from src.strategies.base import BaseStrategy
from src.strategies.fvg_strategy import FVGStrategy
from src.strategies.confluence_strategy import ConfluenceStrategy


def _make_flat_data(n: int = 50) -> pd.DataFrame:
    dates = pd.date_range("2023-01-01", periods=n, freq="D", tz="UTC")
    return pd.DataFrame(
        {
            "open": [100.0] * n,
            "high": [101.0] * n,
            "low": [99.0] * n,
            "close": [100.5] * n,
            "volume": [1000.0] * n,
        },
        index=dates,
    )


def _make_bullish_fvg_data() -> pd.DataFrame:
    """
    5-bar dataset with a bullish FVG at bar index 3.

    Bars:
      0: high=100, close=99 (bearish, irrelevant)
      1: high=103, close=102>open=101 (bullish middle candle)
      2: high=105, close=104
      3: low=106 > high[1]=103  AND  close[2]=104 > open[2]=102
         → bullish FVG at bar 3
      4: filler
    """
    dates = pd.date_range("2023-01-01", periods=5, freq="D", tz="UTC")
    return pd.DataFrame(
        {
            "open": [98.0, 101.0, 102.0, 107.0, 109.0],
            "high": [100.0, 103.0, 105.0, 110.0, 112.0],
            "low": [97.0, 99.0, 101.0, 106.0, 108.0],
            "close": [99.0, 102.0, 104.0, 109.0, 111.0],
            "volume": [1000.0] * 5,
        },
        index=dates,
    )


def _make_bearish_fvg_data() -> pd.DataFrame:
    """
    5-bar dataset with a bearish FVG at bar index 3.

    Bars:
      0: low=106 (reference)
      1: close=105 < open=108 (bearish middle candle)
      2: close=103
      3: high=104 < low[1]=106  AND  close[2]=103 < open[2]=105
         → bearish FVG at bar 3
      4: filler
    """
    dates = pd.date_range("2023-01-01", periods=5, freq="D", tz="UTC")
    return pd.DataFrame(
        {
            "open": [112.0, 108.0, 105.0, 103.0, 100.0],
            "high": [114.0, 110.0, 106.0, 104.0, 102.0],
            "low": [108.0, 106.0, 101.0, 99.0, 97.0],
            "close": [110.0, 105.0, 103.0, 101.0, 99.0],
            "volume": [1000.0] * 5,
        },
        index=dates,
    )


# ---------------------------------------------------------------------------
# BaseStrategy
# ---------------------------------------------------------------------------


class TestBaseStrategy:
    def test_cannot_instantiate_abstract(self):
        with pytest.raises(TypeError):
            BaseStrategy()  # type: ignore[abstract]

    def test_concrete_subclass_must_implement_all(self):
        class Incomplete(BaseStrategy):
            @property
            def name(self):
                return "X"

            def generate_signals(self, data):
                return data

            # Missing: parameters property — still abstract

        with pytest.raises(TypeError):
            Incomplete()  # type: ignore[abstract]


# ---------------------------------------------------------------------------
# FVGStrategy
# ---------------------------------------------------------------------------


class TestFVGStrategy:
    def test_name(self):
        assert FVGStrategy().name == "FVG_Strategy"

    def test_parameters(self):
        s = FVGStrategy(min_gap_atr=0.3, atr_period=10)
        assert s.parameters == {"min_gap_atr": 0.3, "atr_period": 10}

    def test_output_contains_signal_column(self):
        data = _make_flat_data()
        result = FVGStrategy().generate_signals(data)
        assert "signal" in result.columns

    def test_signal_values_are_valid(self):
        data = _make_flat_data(100)
        result = FVGStrategy().generate_signals(data)
        assert set(result["signal"].unique()).issubset({-1, 0, 1})

    def test_no_signal_without_gap(self):
        """Flat data has no gaps → all signals should be 0."""
        data = _make_flat_data()
        result = FVGStrategy(min_gap_atr=0.0).generate_signals(data)
        assert (result["signal"] == 0).all()

    def test_bullish_fvg_signal_generated(self):
        data = _make_bullish_fvg_data()
        result = FVGStrategy(min_gap_atr=0.0).generate_signals(data)
        assert result["signal"].iloc[3] == 1

    def test_bearish_fvg_signal_generated(self):
        data = _make_bearish_fvg_data()
        result = FVGStrategy(min_gap_atr=0.0).generate_signals(data)
        assert result["signal"].iloc[3] == -1

    def test_min_gap_atr_filter_removes_small_gaps(self):
        """With a very high min_gap_atr the small bullish gap should be filtered out."""
        data = _make_bullish_fvg_data()
        result = FVGStrategy(min_gap_atr=1000.0).generate_signals(data)
        # The gap is small relative to a huge ATR multiplier → no buy signal
        assert result["signal"].iloc[3] != 1

    def test_atr_column_present(self):
        data = _make_flat_data(30)
        result = FVGStrategy().generate_signals(data)
        assert "atr" in result.columns
        assert (result["atr"] >= 0).all()

    def test_preserves_original_dataframe(self):
        """generate_signals must not mutate the input DataFrame."""
        data = _make_flat_data()
        original_columns = list(data.columns)
        FVGStrategy().generate_signals(data)
        assert list(data.columns) == original_columns


# ---------------------------------------------------------------------------
# ConfluenceStrategy
# ---------------------------------------------------------------------------


class TestConfluenceStrategy:
    def test_name(self):
        assert ConfluenceStrategy().name == "Confluence_Strategy"

    def test_parameters_include_rsi(self):
        s = ConfluenceStrategy(rsi_period=10, rsi_buy_threshold=45.0)
        params = s.parameters
        assert "rsi_period" in params
        assert params["rsi_period"] == 10
        assert "rsi_buy_threshold" in params

    def test_output_contains_signal_column(self):
        data = _make_flat_data(50)
        result = ConfluenceStrategy().generate_signals(data)
        assert "signal" in result.columns

    def test_rsi_column_present(self):
        data = _make_flat_data(50)
        result = ConfluenceStrategy().generate_signals(data)
        assert "rsi" in result.columns

    def test_rsi_range(self):
        """RSI must be in [0, 100]."""
        data = _make_flat_data(50)
        result = ConfluenceStrategy().generate_signals(data)
        valid = result["rsi"].dropna()
        assert (valid >= 0).all()
        assert (valid <= 100).all()

    def test_filters_out_fvg_without_rsi_confirmation(self):
        """
        A bullish FVG should not produce a buy signal when RSI is above
        rsi_buy_threshold (here set to 0 — impossible to satisfy, so no buys).
        """
        data = _make_bullish_fvg_data()
        result = ConfluenceStrategy(rsi_buy_threshold=0.0).generate_signals(data)
        assert (result["signal"] != 1).all()

    def test_allows_fvg_with_rsi_confirmation(self):
        """
        A bullish FVG should produce a buy signal when RSI <= rsi_buy_threshold.
        rsi_period=2 ensures the warm-up completes within the 5-bar test dataset;
        with all-rising prices RSI=100 and threshold=100, so the condition is met.
        """
        data = _make_bullish_fvg_data()
        result = ConfluenceStrategy(
            min_gap_atr=0.0, rsi_period=2, rsi_buy_threshold=100.0
        ).generate_signals(data)
        assert result["signal"].iloc[3] == 1
