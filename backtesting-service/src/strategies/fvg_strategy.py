import pandas as pd

from .base import BaseStrategy


class FVGStrategy(BaseStrategy):
    """
    Fair Value Gap (FVG) strategy — a Python translation of the Tradie Pine Script.

    Bullish FVG: current low > high from 2 bars ago AND previous candle is bullish.
    Bearish FVG: current high < low from 2 bars ago AND previous candle is bearish.

    Signals are generated at the moment the gap forms.  The min_gap_atr filter
    discards gaps smaller than min_gap_atr * ATR(atr_period), preventing noisy
    entries on tiny imbalances.
    """

    def __init__(self, min_gap_atr: float = 0.5, atr_period: int = 14) -> None:
        self.min_gap_atr = min_gap_atr
        self.atr_period = atr_period

    @property
    def name(self) -> str:
        return "FVG_Strategy"

    @property
    def parameters(self) -> dict:
        return {"min_gap_atr": self.min_gap_atr, "atr_period": self.atr_period}

    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        df = data.copy()

        df["atr"] = self._calculate_atr(df, self.atr_period)

        # Bullish FVG: gap up — current low is above the high from 2 bars ago
        bullish_gap = df["low"] - df["high"].shift(2)
        bullish_candle = df["close"].shift(1) > df["open"].shift(1)
        df["bullish_fvg"] = (
            (bullish_gap > 0) & bullish_candle & (bullish_gap >= df["atr"] * self.min_gap_atr)
        )

        # Bearish FVG: gap down — current high is below the low from 2 bars ago
        bearish_gap = df["low"].shift(2) - df["high"]
        bearish_candle = df["close"].shift(1) < df["open"].shift(1)
        df["bearish_fvg"] = (
            (bearish_gap > 0) & bearish_candle & (bearish_gap >= df["atr"] * self.min_gap_atr)
        )

        df["signal"] = 0
        df.loc[df["bullish_fvg"], "signal"] = 1
        df.loc[df["bearish_fvg"], "signal"] = -1

        return df

    def _calculate_atr(self, df: pd.DataFrame, period: int) -> pd.Series:
        high_low = df["high"] - df["low"]
        high_prev_close = (df["high"] - df["close"].shift(1)).abs()
        low_prev_close = (df["low"] - df["close"].shift(1)).abs()
        true_range = pd.concat([high_low, high_prev_close, low_prev_close], axis=1).max(axis=1)
        return true_range.ewm(span=period, adjust=False).mean()
