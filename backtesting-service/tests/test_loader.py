import asyncio
from datetime import date
from unittest.mock import AsyncMock, MagicMock, patch

import pandas as pd
import pytest

from src.data.loader import HistoricalDataLoader
from src.data.timescale import TimescaleDBPool


def _make_mock_pool(rows=None):
    if rows is None:
        rows = []

    mock_conn = AsyncMock()
    mock_conn.fetch = AsyncMock(return_value=rows)

    mock_pool_instance = MagicMock()
    mock_pool_instance.acquire.return_value.__aenter__ = AsyncMock(return_value=mock_conn)
    mock_pool_instance.acquire.return_value.__aexit__ = AsyncMock(return_value=None)

    mock_db = MagicMock(spec=TimescaleDBPool)
    mock_db.pool = mock_pool_instance

    return mock_db, mock_conn


class TestHistoricalDataLoader:
    @pytest.mark.asyncio
    async def test_returns_empty_dataframe_on_no_rows(self):
        mock_db, _ = _make_mock_pool(rows=[])
        loader = HistoricalDataLoader(mock_db)
        df = await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))
        assert df.empty
        assert list(df.columns) == ["open", "high", "low", "close", "volume"]

    @pytest.mark.asyncio
    async def test_returns_dataframe_with_correct_columns(self):
        from datetime import datetime, timezone

        ts = datetime(2023, 6, 1, tzinfo=timezone.utc)
        rows = [
            {"time": ts, "open": 100.0, "high": 102.0, "low": 99.0, "close": 101.0, "volume": 1500}
        ]
        mock_db, _ = _make_mock_pool(rows=rows)
        loader = HistoricalDataLoader(mock_db)
        df = await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))
        assert not df.empty
        assert set(df.columns) == {"open", "high", "low", "close", "volume"}

    @pytest.mark.asyncio
    async def test_index_is_datetime(self):
        from datetime import datetime, timezone

        ts = datetime(2023, 6, 1, tzinfo=timezone.utc)
        rows = [
            {"time": ts, "open": 100.0, "high": 102.0, "low": 99.0, "close": 101.0, "volume": 500}
        ]
        mock_db, _ = _make_mock_pool(rows=rows)
        loader = HistoricalDataLoader(mock_db)
        df = await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))
        assert isinstance(df.index, pd.DatetimeIndex)

    @pytest.mark.asyncio
    async def test_correct_sql_params_passed(self):
        mock_db, mock_conn = _make_mock_pool(rows=[])
        loader = HistoricalDataLoader(mock_db)
        start = date(2023, 3, 1)
        end = date(2023, 9, 30)
        await loader.load("TSLA", "1H", start, end)
        mock_conn.fetch.assert_called_once()
        call_args = mock_conn.fetch.call_args[0]
        assert "TSLA" in call_args
        assert "1H" in call_args
        assert start in call_args
        assert end in call_args

    @pytest.mark.asyncio
    async def test_exchange_filter_passed_when_specified(self):
        mock_db, mock_conn = _make_mock_pool(rows=[])
        loader = HistoricalDataLoader(mock_db)
        start = date(2023, 1, 1)
        end = date(2023, 12, 31)
        await loader.load("AAPL", "1D", start, end, exchange="NASDAQ")
        mock_conn.fetch.assert_called_once()
        call_args = mock_conn.fetch.call_args[0]
        assert "NASDAQ" in call_args

    @pytest.mark.asyncio
    async def test_exchange_none_when_not_specified(self):
        mock_db, mock_conn = _make_mock_pool(rows=[])
        loader = HistoricalDataLoader(mock_db)
        await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))
        call_args = mock_conn.fetch.call_args[0]
        # None is passed as the exchange param so the SQL NULL check disables the filter
        assert None in call_args

    @pytest.mark.asyncio
    async def test_propagates_db_error(self):
        mock_conn = AsyncMock()
        mock_conn.fetch = AsyncMock(side_effect=Exception("Connection refused"))
        mock_pool_instance = MagicMock()
        mock_pool_instance.acquire.return_value.__aenter__ = AsyncMock(return_value=mock_conn)
        mock_pool_instance.acquire.return_value.__aexit__ = AsyncMock(return_value=None)
        mock_db = MagicMock(spec=TimescaleDBPool)
        mock_db.pool = mock_pool_instance

        loader = HistoricalDataLoader(mock_db)
        with pytest.raises(RuntimeError, match="Failed to load OHLCV data"):
            await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))

    @pytest.mark.asyncio
    async def test_numeric_columns_are_float(self):
        from datetime import datetime, timezone

        ts = datetime(2023, 6, 1, tzinfo=timezone.utc)
        rows = [{"time": ts, "open": 100, "high": 102, "low": 99, "close": 101, "volume": 1000}]
        mock_db, _ = _make_mock_pool(rows=rows)
        loader = HistoricalDataLoader(mock_db)
        df = await loader.load("AAPL", "1D", date(2023, 1, 1), date(2023, 12, 31))
        for col in ["open", "high", "low", "close", "volume"]:
            assert df[col].dtype == float, f"Column {col} should be float"


class TestTimescaleDBPool:
    def test_pool_raises_when_not_initialized(self):
        pool = TimescaleDBPool()
        with pytest.raises(RuntimeError, match="not initialized"):
            _ = pool.pool

    def test_is_connected_false_before_init(self):
        pool = TimescaleDBPool()
        assert pool.is_connected is False

    @pytest.mark.asyncio
    async def test_close_when_not_connected_does_nothing(self):
        pool = TimescaleDBPool()
        await pool.close()  # should not raise
