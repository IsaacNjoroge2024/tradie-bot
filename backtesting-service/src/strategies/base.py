from abc import ABC, abstractmethod

import pandas as pd


class BaseStrategy(ABC):
    """Abstract base class for all backtesting strategies."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Human-readable strategy name."""

    @abstractmethod
    def generate_signals(self, data: pd.DataFrame) -> pd.DataFrame:
        """
        Generate buy/sell/hold signals from OHLCV data.

        Args:
            data: DataFrame with columns open, high, low, close, volume
                  indexed by datetime.

        Returns:
            DataFrame containing at minimum a 'signal' column where:
              1  = buy (enter long)
             -1  = sell (exit long / enter short)
              0  = hold
        """

    @property
    @abstractmethod
    def parameters(self) -> dict:
        """Current strategy parameters as a dict."""
