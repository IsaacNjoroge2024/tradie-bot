import pandas as pd

from .base import BaseStrategy
from .fvg_strategy import FVGStrategy


class ConfluenceStrategy(BaseStrategy):
    """
    Confluence strategy: FVG signal confirmed by RSI momentum.

    Entry rules:
      - Buy:  bullish FVG present AND RSI < rsi_buy_threshold  (momentum oversold/neutral)
      - Sell: bearish FVG present AND RSI > rsi_sell_threshold (momentum overbought/neutral)

    The RSI filter removes counter-trend FVG entries, keeping only setups where
    price momentum aligns with the gap direction.
    """

    def __init__(
        self,
        min_gap_atr: float = 0.5,
        atr_period: int = 14,
        rsi_period: int = 14,
        rsi_buy_threshold: float = 50.0,
        rsi_sell_threshold: float = 50.0,
    ) -> None:
        self._fvg = FVGStrategy(min_gap_atr=min_gap_atr, atr_period=atr_period)
        self.rsi_period = rsi_period
        self.rsi_buy_threshold = rsi_buy_threshold
        self.rsi_sell_threshold = rsi_sell_threshold

    @property
    def name(self) -> str:
        return "Confluence_Strategy"

    @property
    def parameters(self) -> dict:
        return {
            **self._fvg.parameters,
            "rsi_period": self.rsi_period,
            "rsi_buy_threshold": self.rsi_buy_threshold,
            "rsi_sell_threshold": self.rsi_sell_threshold,
        }

    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        df = self._fvg.generate_signals(data)

        df["rsi"] = self._calculate_rsi(df["close"], self.rsi_period)

        # Require RSI alignment before accepting the FVG signal (inclusive thresholds)
        buy_condition = df["bullish_fvg"] & (df["rsi"] <= self.rsi_buy_threshold)
        sell_condition = df["bearish_fvg"] & (df["rsi"] >= self.rsi_sell_threshold)

        df["signal"] = 0
        df.loc[buy_condition, "signal"] = 1
        df.loc[sell_condition, "signal"] = -1

        return df

    def _calculate_rsi(self, prices: pd.Series, period: int) -> pd.Series:
        delta = prices.diff()
        gain = delta.where(delta > 0, 0.0)
        loss = (-delta).where(delta < 0, 0.0)
        avg_gain = gain.ewm(span=period, adjust=False, min_periods=period).mean()
        avg_loss = loss.ewm(span=period, adjust=False, min_periods=period).mean()
        rs = avg_gain / avg_loss.replace(0, float("nan"))
        rsi = 100.0 - (100.0 / (1.0 + rs))
        # Flat market (no movement): RSI = 50 (neutral), not overbought
        flat = (avg_gain == 0) & (avg_loss == 0)
        rsi = rsi.mask(flat, 50.0)
        # All gains, no losses: RSI = 100 (overbought)
        rsi = rsi.mask((avg_loss == 0) & (avg_gain > 0), 100.0)
        return rsi
