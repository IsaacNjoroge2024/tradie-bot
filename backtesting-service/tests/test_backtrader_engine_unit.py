import pandas as pd
import pytest

from src.engine.backtrader_engine import BacktraderEngine, BacktraderEngineError


def test_raises_on_empty_data():
    engine = BacktraderEngine()
    empty_data = pd.DataFrame(columns=["open", "high", "low", "close", "volume"])
    empty_signals = pd.DataFrame(columns=["signal"])
    with pytest.raises(BacktraderEngineError, match="non-empty OHLCV data"):
        engine.run_backtest(empty_data, empty_signals)
