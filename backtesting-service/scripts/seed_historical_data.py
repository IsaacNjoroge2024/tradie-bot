#!/usr/bin/env python3
"""
Seed historical OHLCV data from Yahoo Finance into TimescaleDB.

Reads DB credentials from the project root .env file automatically.

Usage (from backtesting-service/ directory):
    pip install yfinance
    python scripts/seed_historical_data.py

Edit SYMBOLS, TIMEFRAMES, START_DATE, END_DATE below to control what is seeded.
"""

import asyncio
import sys
from datetime import timezone
from pathlib import Path

# Make backtesting-service/ importable when running this script directly
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# Load .env from project root before importing config
from dotenv import load_dotenv

_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"
load_dotenv(dotenv_path=_ENV_FILE)

try:
    import yfinance as yf
except ImportError:
    print("yfinance is not installed. Run: pip install yfinance")
    sys.exit(1)

import asyncpg
from src.config import settings

# ---------------------------------------------------------------------------
# Configuration — edit these to control what gets seeded
# ---------------------------------------------------------------------------

SYMBOLS = [
    ("AAPL", "NASDAQ"),
    ("MSFT", "NASDAQ"),
    ("TSLA", "NASDAQ"),
    ("SPY", "NYSE"),
    ("QQQ", "NASDAQ"),
]

# Maps our DB timeframe label → (yfinance interval, start_date, end_date).
# Yahoo Finance only provides 1H data for the last 730 days; 1D has no limit.
import datetime as _dt

_today = _dt.date.today().isoformat()
_two_years_ago = (_dt.date.today() - _dt.timedelta(days=720)).isoformat()

TIMEFRAMES = {
    "1D": ("1d", "2020-01-01", _today),
    "1H": ("1h", _two_years_ago, _today),  # within Yahoo's 730-day intraday limit
}

# ---------------------------------------------------------------------------


async def seed() -> None:
    conn = await asyncpg.connect(
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    print(f"Connected to TimescaleDB at {settings.db_host}:{settings.db_port}/{settings.db_name}")

    total = 0

    for symbol, exchange in SYMBOLS:
        for tf_label, (yf_interval, start, end) in TIMEFRAMES.items():
            print(f"  Downloading {symbol}/{tf_label} ({yf_interval}) from Yahoo Finance...")
            try:
                df = yf.Ticker(symbol).history(
                    start=start,
                    end=end,
                    interval=yf_interval,
                    auto_adjust=True,
                )
            except Exception as exc:
                print(f"    ERROR downloading {symbol}/{tf_label}: {exc}")
                continue

            if df.empty:
                print(f"    No data returned for {symbol}/{tf_label}")
                continue

            records = []
            for ts, row in df.iterrows():
                # Ensure timestamp is timezone-aware (UTC)
                if ts.tzinfo is None:
                    ts = ts.replace(tzinfo=timezone.utc)
                records.append(
                    (
                        ts,
                        symbol,
                        exchange,
                        tf_label,
                        float(row["Open"]),
                        float(row["High"]),
                        float(row["Low"]),
                        float(row["Close"]),
                        int(row["Volume"]),
                    )
                )

            await conn.executemany(
                """
                INSERT INTO ohlcv (time, symbol, exchange, timeframe, open, high, low, close, volume)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
                ON CONFLICT (time, symbol, exchange, timeframe) DO UPDATE
                    SET open   = EXCLUDED.open,
                        high   = EXCLUDED.high,
                        low    = EXCLUDED.low,
                        close  = EXCLUDED.close,
                        volume = EXCLUDED.volume
                """,
                records,
            )
            print(f"    Inserted/updated {len(records)} candles for {symbol}/{tf_label}")
            total += len(records)

    await conn.close()
    print(f"\nDone. Total rows seeded: {total}")


if __name__ == "__main__":
    asyncio.run(seed())
