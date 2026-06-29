import logging
from datetime import date

import pandas as pd

from .timescale import TimescaleDBPool

logger = logging.getLogger(__name__)


class HistoricalDataLoader:
    """Loads historical OHLCV data from TimescaleDB."""

    def __init__(self, db_pool: TimescaleDBPool) -> None:
        self._db = db_pool

    async def load(
        self,
        symbol: str,
        timeframe: str,
        start_date: date,
        end_date: date,
    ) -> pd.DataFrame:
        """
        Load OHLCV candles from TimescaleDB.

        Returns a DataFrame indexed by datetime with columns:
        open, high, low, close, volume
        """
        query = """
            SELECT time, open, high, low, close, volume
            FROM ohlcv
            WHERE symbol = $1
              AND timeframe = $2
              AND time BETWEEN $3 AND $4
            ORDER BY time ASC
        """
        try:
            async with self._db.pool.acquire() as conn:
                rows = await conn.fetch(query, symbol, timeframe, start_date, end_date)
        except Exception as e:
            logger.error(f"Failed to load OHLCV data for {symbol}/{timeframe}: {e}")
            raise

        if not rows:
            logger.warning(
                f"No OHLCV data found for {symbol}/{timeframe} "
                f"between {start_date} and {end_date}"
            )
            return pd.DataFrame(columns=["open", "high", "low", "close", "volume"])

        df = pd.DataFrame(rows, columns=["time", "open", "high", "low", "close", "volume"])
        df["time"] = pd.to_datetime(df["time"], utc=True)
        df = df.set_index("time")
        df = df.astype(
            {"open": float, "high": float, "low": float, "close": float, "volume": float}
        )

        logger.info(f"Loaded {len(df)} candles for {symbol}/{timeframe}")
        return df

    async def get_available_symbols(self) -> list[str]:
        """Return list of symbols present in the ohlcv table."""
        query = "SELECT DISTINCT symbol FROM ohlcv ORDER BY symbol"
        async with self._db.pool.acquire() as conn:
            rows = await conn.fetch(query)
        return [r["symbol"] for r in rows]
