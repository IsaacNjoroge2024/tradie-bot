"""
Load historical OHLCV candle data into TimescaleDB using yfinance.

Usage:
    pip install yfinance psycopg2-binary python-dotenv
    python scripts/load_ohlcv.py

Reads DB credentials from the .env file in the project root.
Covers all asset classes Tradie supports: stocks, ETFs, futures, crypto, forex.

Timeframe limits (yfinance):
    15m  → max 60 days  |  1h → max 730 days (~2 years)  |  1d → unlimited
"""

import sys
import time
from datetime import datetime, timezone
from pathlib import Path

try:
    import yfinance as yf
except ImportError:
    sys.exit("Missing dependency: pip install yfinance")

try:
    import psycopg2
except ImportError:
    sys.exit("Missing dependency: pip install psycopg2-binary")

try:
    from dotenv import dotenv_values
except ImportError:
    sys.exit("Missing dependency: pip install python-dotenv")


# ── Load .env ─────────────────────────────────────────────────────────────────

ENV_FILE = Path(__file__).resolve().parent.parent / ".env"
if not ENV_FILE.exists():
    sys.exit(f".env file not found at {ENV_FILE}")

env = dotenv_values(ENV_FILE)

DB_CONFIG = dict(
    host=env.get("DB_HOST", "localhost"),
    port=int(env.get("DB_PORT", 5433)),
    dbname=env.get("DB_NAME", "tradie"),
    user=env.get("DB_USER", "tradie"),
    password=env.get("DB_PASSWORD", ""),
)

INSERT_SQL = """
    INSERT INTO ohlcv (time, symbol, exchange, timeframe, open, high, low, close, volume)
    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    ON CONFLICT DO NOTHING
"""


# ── Core loader ───────────────────────────────────────────────────────────────

def load(symbol: str, exchange: str, timeframe: str,
         yf_interval: str, period: str, job_num: int, total: int) -> int:
    print(f"  [{job_num}/{total}] {symbol:<12} {timeframe:<4} period={period:<5} ...",
          end=" ", flush=True)
    try:
        df = yf.download(
            symbol,
            period=period,
            interval=yf_interval,
            auto_adjust=True,
            progress=False,
        )
    except Exception as e:
        print(f"SKIP (download error: {e})")
        return 0

    if df.empty:
        print("SKIP (no data)")
        return 0

    # Flatten MultiIndex columns if present (yfinance quirk with single tickers)
    if hasattr(df.columns, "levels"):
        df.columns = df.columns.get_level_values(0)

    rows = []
    for ts, row in df.iterrows():
        try:
            t = ts.to_pydatetime()
            t = t.replace(tzinfo=timezone.utc) if t.tzinfo is None else t.astimezone(timezone.utc)
            rows.append((
                t, symbol, exchange, timeframe,
                float(row["Open"]), float(row["High"]),
                float(row["Low"]), float(row["Close"]),
                int(row["Volume"]),
            ))
        except Exception:
            continue

    if not rows:
        print("SKIP (no valid rows)")
        return 0

    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.executemany(INSERT_SQL, rows)
        inserted = cur.rowcount
        conn.commit()
        cur.close()
        conn.close()
    except Exception as e:
        print(f"SKIP (db error: {e})")
        return 0

    print(f"{len(rows)} bars → {inserted} inserted")
    return inserted


# ── Symbol definitions ────────────────────────────────────────────────────────
#
# exchange values are chosen to match strategy-engine detectAssetClass():
#   contains "CRYPTO"  → CRYPTO asset class
#   contains "FOREX"   → FOREX asset class
#   contains "CME"     → FUTURES asset class
#   anything else      → STK (stocks/ETFs)

# Each group is: (yf_symbol, exchange, tradie_symbol_alias)
# tradie_symbol_alias = what gets stored as `symbol` in the DB
# For most assets this is the same as yf_symbol.

# ── Helper: build jobs from symbol groups ─────────────────────────────────────
# timeframe_sets: list of (tradie_timeframe, yf_interval, period)
def make_jobs(symbols, timeframe_sets):
    jobs = []
    for (yf_sym, exchange) in symbols:
        for (tf, interval, period) in timeframe_sets:
            jobs.append((yf_sym, exchange, tf, interval, period))
    return jobs


# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — Major US Index ETFs
# ─────────────────────────────────────────────────────────────────────────────
INDEX_ETFS = [
    ("SPY",  "NYSE"),    # S&P 500
    ("QQQ",  "NASDAQ"),  # Nasdaq 100
    ("IWM",  "NYSE"),    # Russell 2000
    ("DIA",  "NYSE"),    # Dow Jones 30
    ("MDY",  "NYSE"),    # S&P 400 Mid Cap
    ("VTI",  "NYSE"),    # Total US Market
    ("VOO",  "NYSE"),    # S&P 500 (Vanguard)
    ("IVV",  "NYSE"),    # S&P 500 (iShares)
    ("VEA",  "NYSE"),    # Developed Markets ex-US
    ("VWO",  "NYSE"),    # Emerging Markets
    ("EFA",  "NYSE"),    # MSCI EAFE
    ("EEM",  "NYSE"),    # MSCI Emerging Markets
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — Sector ETFs (SPDR)
# ─────────────────────────────────────────────────────────────────────────────
SECTOR_ETFS = [
    ("XLK",  "NYSE"),    # Technology
    ("XLF",  "NYSE"),    # Financials
    ("XLV",  "NYSE"),    # Healthcare
    ("XLE",  "NYSE"),    # Energy
    ("XLI",  "NYSE"),    # Industrials
    ("XLY",  "NYSE"),    # Consumer Discretionary
    ("XLP",  "NYSE"),    # Consumer Staples
    ("XLB",  "NYSE"),    # Materials
    ("XLU",  "NYSE"),    # Utilities
    ("XLRE", "NYSE"),    # Real Estate
    ("XLC",  "NYSE"),    # Communication Services
    ("SMH",  "NASDAQ"),  # Semiconductors
    ("IBB",  "NASDAQ"),  # Biotech
    ("XBI",  "NYSE"),    # Biotech (equal weight)
    ("XHB",  "NYSE"),    # Homebuilders
    ("XRT",  "NYSE"),    # Retail
    ("KRE",  "NYSE"),    # Regional Banks
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — Commodity, Bond & Volatility ETFs
# ─────────────────────────────────────────────────────────────────────────────
COMMODITY_BOND_ETFS = [
    ("GLD",  "NYSE"),    # Gold
    ("SLV",  "NYSE"),    # Silver
    ("IAU",  "NYSE"),    # Gold (iShares)
    ("GDX",  "NYSE"),    # Gold Miners
    ("GDXJ", "NYSE"),    # Junior Gold Miners
    ("USO",  "NYSE"),    # Crude Oil
    ("UNG",  "NYSE"),    # Natural Gas
    ("DBC",  "NYSE"),    # Commodities Basket
    ("CORN", "NYSE"),    # Corn
    ("WEAT", "NYSE"),    # Wheat
    ("SOYB", "NYSE"),    # Soybeans
    ("TLT",  "NYSE"),    # 20+ Year Treasury
    ("IEF",  "NYSE"),    # 7-10 Year Treasury
    ("SHY",  "NYSE"),    # 1-3 Year Treasury
    ("HYG",  "NYSE"),    # High Yield Corporate Bond
    ("LQD",  "NYSE"),    # Investment Grade Corp Bond
    ("BND",  "NYSE"),    # Total Bond Market
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — Leveraged / Inverse ETFs (popular for day trading)
# ─────────────────────────────────────────────────────────────────────────────
LEVERAGED_ETFS = [
    ("TQQQ", "NASDAQ"),  # 3x Nasdaq 100 Bull
    ("SQQQ", "NASDAQ"),  # 3x Nasdaq 100 Bear
    ("SPXL", "NYSE"),    # 3x S&P 500 Bull
    ("SPXS", "NYSE"),    # 3x S&P 500 Bear
    ("UPRO", "NYSE"),    # 3x S&P 500 Bull
    ("SOXL", "NYSE"),    # 3x Semiconductors Bull
    ("SOXS", "NYSE"),    # 3x Semiconductors Bear
    ("LABU", "NYSE"),    # 3x Biotech Bull
    ("LABD", "NYSE"),    # 3x Biotech Bear
    ("TNA",  "NYSE"),    # 3x Russell 2000 Bull
    ("FAZ",  "NYSE"),    # 3x Financials Bear
    ("FAS",  "NYSE"),    # 3x Financials Bull
    ("UVXY", "NYSE"),    # 1.5x VIX Short-Term
    ("SVXY", "NYSE"),    # -0.5x VIX
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — US Large Cap Technology
# ─────────────────────────────────────────────────────────────────────────────
TECH_STOCKS = [
    ("AAPL",  "NASDAQ"),  # Apple
    ("MSFT",  "NASDAQ"),  # Microsoft
    ("NVDA",  "NASDAQ"),  # Nvidia
    ("GOOGL", "NASDAQ"),  # Alphabet A
    ("GOOG",  "NASDAQ"),  # Alphabet C
    ("META",  "NASDAQ"),  # Meta
    ("AMZN",  "NASDAQ"),  # Amazon
    ("TSLA",  "NASDAQ"),  # Tesla
    ("AVGO",  "NASDAQ"),  # Broadcom
    ("ORCL",  "NYSE"),    # Oracle
    ("AMD",   "NASDAQ"),  # AMD
    ("INTC",  "NASDAQ"),  # Intel
    ("QCOM",  "NASDAQ"),  # Qualcomm
    ("CRM",   "NYSE"),    # Salesforce
    ("ADBE",  "NASDAQ"),  # Adobe
    ("NFLX",  "NASDAQ"),  # Netflix
    ("CSCO",  "NASDAQ"),  # Cisco
    ("TXN",   "NASDAQ"),  # Texas Instruments
    ("AMAT",  "NASDAQ"),  # Applied Materials
    ("MU",    "NASDAQ"),  # Micron
    ("LRCX",  "NASDAQ"),  # Lam Research
    ("KLAC",  "NASDAQ"),  # KLA Corp
    ("NOW",   "NYSE"),    # ServiceNow
    ("PANW",  "NASDAQ"),  # Palo Alto Networks
    ("CRWD",  "NASDAQ"),  # CrowdStrike
    ("SNOW",  "NYSE"),    # Snowflake
    ("PLTR",  "NYSE"),    # Palantir
    ("MSTR",  "NASDAQ"),  # MicroStrategy (BTC proxy)
    ("MARA",  "NASDAQ"),  # Marathon Digital (BTC miner)
    ("RIOT",  "NASDAQ"),  # Riot Platforms (BTC miner)
    ("COIN",  "NASDAQ"),  # Coinbase
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — Finance, Healthcare, Consumer, Energy, Industrial
# ─────────────────────────────────────────────────────────────────────────────
BLUE_CHIPS = [
    # Finance
    ("JPM",  "NYSE"),    # JPMorgan Chase
    ("BAC",  "NYSE"),    # Bank of America
    ("GS",   "NYSE"),    # Goldman Sachs
    ("MS",   "NYSE"),    # Morgan Stanley
    ("BLK",  "NYSE"),    # BlackRock
    ("V",    "NYSE"),    # Visa
    ("MA",   "NYSE"),    # Mastercard
    ("PYPL", "NASDAQ"),  # PayPal
    # Healthcare
    ("UNH",  "NYSE"),    # UnitedHealth
    ("JNJ",  "NYSE"),    # Johnson & Johnson
    ("LLY",  "NYSE"),    # Eli Lilly
    ("ABBV", "NYSE"),    # AbbVie
    ("MRK",  "NYSE"),    # Merck
    ("PFE",  "NYSE"),    # Pfizer
    ("TMO",  "NYSE"),    # Thermo Fisher
    ("ABT",  "NYSE"),    # Abbott Labs
    # Consumer
    ("AMZN", "NASDAQ"),  # Amazon (also tech)
    ("WMT",  "NYSE"),    # Walmart
    ("HD",   "NYSE"),    # Home Depot
    ("MCD",  "NYSE"),    # McDonald's
    ("SBUX", "NASDAQ"),  # Starbucks
    ("NKE",  "NYSE"),    # Nike
    ("KO",   "NYSE"),    # Coca-Cola
    ("PEP",  "NASDAQ"),  # PepsiCo
    ("PG",   "NYSE"),    # Procter & Gamble
    ("DIS",  "NYSE"),    # Disney
    # Energy
    ("XOM",  "NYSE"),    # ExxonMobil
    ("CVX",  "NYSE"),    # Chevron
    ("COP",  "NYSE"),    # ConocoPhillips
    ("SLB",  "NYSE"),    # Schlumberger
    ("OXY",  "NYSE"),    # Occidental Petroleum
    # Industrial / Defense
    ("BA",   "NYSE"),    # Boeing
    ("LMT",  "NYSE"),    # Lockheed Martin
    ("RTX",  "NYSE"),    # Raytheon
    ("GE",   "NYSE"),    # GE Aerospace
    ("CAT",  "NYSE"),    # Caterpillar
    ("HON",  "NASDAQ"),  # Honeywell
    ("UPS",  "NYSE"),    # UPS
    ("FDX",  "NYSE"),    # FedEx
]

# ─────────────────────────────────────────────────────────────────────────────
# STOCKS — High-beta / momentum / popular swing & day trading
# ─────────────────────────────────────────────────────────────────────────────
MOMENTUM_STOCKS = [
    ("TSLA",  "NASDAQ"),  # Tesla (also in tech)
    ("GME",   "NYSE"),    # GameStop
    ("AMC",   "NYSE"),    # AMC Entertainment
    ("RIVN",  "NASDAQ"),  # Rivian
    ("LCID",  "NASDAQ"),  # Lucid Motors
    ("SOFI",  "NASDAQ"),  # SoFi Technologies
    ("HOOD",  "NASDAQ"),  # Robinhood
    ("RBLX",  "NYSE"),    # Roblox
    ("SNAP",  "NYSE"),    # Snap
    ("UBER",  "NYSE"),    # Uber
    ("LYFT",  "NASDAQ"),  # Lyft
    ("ABNB",  "NASDAQ"),  # Airbnb
    ("DASH",  "NYSE"),    # DoorDash
    ("PATH",  "NYSE"),    # UiPath
    ("AI",    "NYSE"),    # C3.ai
    ("IONQ",  "NYSE"),    # IonQ (quantum)
    ("RGTI",  "NASDAQ"),  # Rigetti Computing
    ("SMCI",  "NASDAQ"),  # Super Micro Computer
    ("ARM",   "NASDAQ"),  # Arm Holdings
    ("HOOD",  "NASDAQ"),  # Robinhood
]

# ─────────────────────────────────────────────────────────────────────────────
# FUTURES — CME/CBOT (Yahoo Finance uses =F suffix)
# Exchange = "CME" → detectAssetClass returns FUTURES
# ─────────────────────────────────────────────────────────────────────────────
FUTURES = [
    # Equity Index Futures
    ("ES=F",  "CME"),    # E-mini S&P 500
    ("NQ=F",  "CME"),    # E-mini Nasdaq 100
    ("YM=F",  "CME"),    # E-mini Dow Jones 30
    ("RTY=F", "CME"),    # E-mini Russell 2000
    ("MES=F", "CME"),    # Micro E-mini S&P 500
    ("MNQ=F", "CME"),    # Micro E-mini Nasdaq 100
    ("MYM=F", "CME"),    # Micro E-mini Dow Jones
    ("M2K=F", "CME"),    # Micro E-mini Russell 2000
    # Metals
    ("GC=F",  "CME"),    # Gold
    ("SI=F",  "CME"),    # Silver
    ("HG=F",  "CME"),    # Copper
    ("PL=F",  "CME"),    # Platinum
    ("PA=F",  "CME"),    # Palladium
    # Energy
    ("CL=F",  "CME"),    # Crude Oil WTI
    ("BZ=F",  "CME"),    # Brent Crude Oil
    ("NG=F",  "CME"),    # Natural Gas
    ("RB=F",  "CME"),    # RBOB Gasoline
    ("HO=F",  "CME"),    # Heating Oil
    # Grains (CBOT)
    ("ZC=F",  "CME"),    # Corn
    ("ZS=F",  "CME"),    # Soybeans
    ("ZW=F",  "CME"),    # Wheat
    ("ZO=F",  "CME"),    # Oats
    ("ZR=F",  "CME"),    # Rough Rice
    # Softs
    ("CC=F",  "CME"),    # Cocoa
    ("KC=F",  "CME"),    # Coffee
    ("CT=F",  "CME"),    # Cotton
    ("SB=F",  "CME"),    # Sugar
    ("OJ=F",  "CME"),    # Orange Juice
    # Interest Rates (CBOT)
    ("ZN=F",  "CME"),    # 10-Year T-Note
    ("ZB=F",  "CME"),    # 30-Year T-Bond
    ("ZF=F",  "CME"),    # 5-Year T-Note
    ("ZT=F",  "CME"),    # 2-Year T-Note
    # Currency Futures (CME)
    ("6E=F",  "CME"),    # Euro FX
    ("6J=F",  "CME"),    # Japanese Yen
    ("6B=F",  "CME"),    # British Pound
    ("6A=F",  "CME"),    # Australian Dollar
    ("6C=F",  "CME"),    # Canadian Dollar
    ("6S=F",  "CME"),    # Swiss Franc
    ("6N=F",  "CME"),    # New Zealand Dollar
    ("6M=F",  "CME"),    # Mexican Peso
]

# ─────────────────────────────────────────────────────────────────────────────
# CRYPTOCURRENCY (Yahoo Finance uses -USD suffix)
# Exchange = "CRYPTO" → detectAssetClass returns CRYPTO
# ─────────────────────────────────────────────────────────────────────────────
CRYPTO = [
    # Layer 1 — Large cap
    ("BTC-USD",  "CRYPTO"),  # Bitcoin
    ("ETH-USD",  "CRYPTO"),  # Ethereum
    ("BNB-USD",  "CRYPTO"),  # BNB (Binance Coin)
    ("SOL-USD",  "CRYPTO"),  # Solana
    ("XRP-USD",  "CRYPTO"),  # XRP
    ("ADA-USD",  "CRYPTO"),  # Cardano
    ("AVAX-USD", "CRYPTO"),  # Avalanche
    ("DOT-USD",  "CRYPTO"),  # Polkadot
    ("MATIC-USD","CRYPTO"),  # Polygon
    ("ATOM-USD", "CRYPTO"),  # Cosmos
    ("NEAR-USD", "CRYPTO"),  # NEAR Protocol
    ("APT-USD",  "CRYPTO"),  # Aptos
    ("SUI-USD",  "CRYPTO"),  # Sui
    ("TRX-USD",  "CRYPTO"),  # TRON
    ("XLM-USD",  "CRYPTO"),  # Stellar
    # Layer 2 / DeFi
    ("ARB-USD",  "CRYPTO"),  # Arbitrum
    ("OP-USD",   "CRYPTO"),  # Optimism
    ("LINK-USD", "CRYPTO"),  # Chainlink
    ("UNI-USD",  "CRYPTO"),  # Uniswap
    ("AAVE-USD", "CRYPTO"),  # Aave
    ("MKR-USD",  "CRYPTO"),  # Maker
    ("CRV-USD",  "CRYPTO"),  # Curve
    ("SNX-USD",  "CRYPTO"),  # Synthetix
    # Meme / High beta
    ("DOGE-USD", "CRYPTO"),  # Dogecoin
    ("SHIB-USD", "CRYPTO"),  # Shiba Inu
    ("PEPE-USD", "CRYPTO"),  # Pepe
    # Other major
    ("LTC-USD",  "CRYPTO"),  # Litecoin
    ("BCH-USD",  "CRYPTO"),  # Bitcoin Cash
    ("FIL-USD",  "CRYPTO"),  # Filecoin
    ("ICP-USD",  "CRYPTO"),  # Internet Computer
    ("HBAR-USD", "CRYPTO"),  # Hedera
    ("VET-USD",  "CRYPTO"),  # VeChain
    ("ALGO-USD", "CRYPTO"),  # Algorand
    ("FTM-USD",  "CRYPTO"),  # Fantom
    ("INJ-USD",  "CRYPTO"),  # Injective
    ("SEI-USD",  "CRYPTO"),  # Sei
]

# ─────────────────────────────────────────────────────────────────────────────
# FOREX (Yahoo Finance uses =X suffix)
# Exchange = "FOREX" → detectAssetClass returns FOREX
# ─────────────────────────────────────────────────────────────────────────────
FOREX = [
    # ── Major pairs (USD base or quote) ──────────────────────────────────────
    ("EURUSD=X", "FOREX"),  # Euro / US Dollar
    ("GBPUSD=X", "FOREX"),  # British Pound / US Dollar
    ("USDJPY=X", "FOREX"),  # US Dollar / Japanese Yen
    ("USDCHF=X", "FOREX"),  # US Dollar / Swiss Franc
    ("AUDUSD=X", "FOREX"),  # Australian Dollar / US Dollar
    ("USDCAD=X", "FOREX"),  # US Dollar / Canadian Dollar
    ("NZDUSD=X", "FOREX"),  # New Zealand Dollar / US Dollar
    # ── EUR crosses ──────────────────────────────────────────────────────────
    ("EURGBP=X", "FOREX"),  # Euro / British Pound
    ("EURJPY=X", "FOREX"),  # Euro / Japanese Yen
    ("EURCHF=X", "FOREX"),  # Euro / Swiss Franc
    ("EURAUD=X", "FOREX"),  # Euro / Australian Dollar
    ("EURCAD=X", "FOREX"),  # Euro / Canadian Dollar
    ("EURNZD=X", "FOREX"),  # Euro / New Zealand Dollar
    # ── GBP crosses ──────────────────────────────────────────────────────────
    ("GBPJPY=X", "FOREX"),  # British Pound / Japanese Yen
    ("GBPCHF=X", "FOREX"),  # British Pound / Swiss Franc
    ("GBPAUD=X", "FOREX"),  # British Pound / Australian Dollar
    ("GBPCAD=X", "FOREX"),  # British Pound / Canadian Dollar
    ("GBPNZD=X", "FOREX"),  # British Pound / New Zealand Dollar
    # ── AUD crosses ──────────────────────────────────────────────────────────
    ("AUDJPY=X", "FOREX"),  # Australian Dollar / Japanese Yen
    ("AUDCHF=X", "FOREX"),  # Australian Dollar / Swiss Franc
    ("AUDCAD=X", "FOREX"),  # Australian Dollar / Canadian Dollar
    ("AUDNZD=X", "FOREX"),  # Australian Dollar / New Zealand Dollar
    # ── Other crosses ────────────────────────────────────────────────────────
    ("CADJPY=X", "FOREX"),  # Canadian Dollar / Japanese Yen
    ("CADCHF=X", "FOREX"),  # Canadian Dollar / Swiss Franc
    ("CHFJPY=X", "FOREX"),  # Swiss Franc / Japanese Yen
    ("NZDJPY=X", "FOREX"),  # New Zealand Dollar / Japanese Yen
    ("NZDCHF=X", "FOREX"),  # New Zealand Dollar / Swiss Franc
    ("NZDCAD=X", "FOREX"),  # New Zealand Dollar / Canadian Dollar
    # ── Exotic pairs ─────────────────────────────────────────────────────────
    ("USDZAR=X", "FOREX"),  # US Dollar / South African Rand
    ("USDMXN=X", "FOREX"),  # US Dollar / Mexican Peso
    ("USDSGD=X", "FOREX"),  # US Dollar / Singapore Dollar
    ("USDNOK=X", "FOREX"),  # US Dollar / Norwegian Krone
    ("USDSEK=X", "FOREX"),  # US Dollar / Swedish Krona
    ("USDHKD=X", "FOREX"),  # US Dollar / Hong Kong Dollar
    ("USDTRY=X", "FOREX"),  # US Dollar / Turkish Lira
    ("USDINR=X", "FOREX"),  # US Dollar / Indian Rupee
    ("USDCNH=X", "FOREX"),  # US Dollar / Chinese Yuan (offshore)
    ("EURTRY=X", "FOREX"),  # Euro / Turkish Lira
    ("GBPTRY=X", "FOREX"),  # British Pound / Turkish Lira
]


# ─────────────────────────────────────────────────────────────────────────────
# Build full job list
# ─────────────────────────────────────────────────────────────────────────────
#
# Timeframe sets per asset class:
#
#   STOCKS/ETFS  → 15M (60d intraday), 1H (2yr), 1D (5yr)
#   FUTURES      → 15M (60d), 1H (2yr), 1D (5yr)
#   CRYPTO       → 15M (60d), 1H (2yr), 1D (5yr)
#   FOREX        → 15M (60d), 1H (2yr), 1D (5yr)

STOCK_TIMEFRAMES = [
    ("15M", "15m", "60d"),
    ("1H",  "1h",  "2y"),
    ("1D",  "1d",  "5y"),
]

FUTURES_TIMEFRAMES = [
    ("15M", "15m", "60d"),
    ("1H",  "1h",  "2y"),
    ("1D",  "1d",  "max"),
]

CRYPTO_TIMEFRAMES = [
    ("15M", "15m", "60d"),
    ("1H",  "1h",  "2y"),
    ("1D",  "1d",  "max"),
]

FOREX_TIMEFRAMES = [
    ("15M", "15m", "60d"),
    ("1H",  "1h",  "2y"),
    ("1D",  "1d",  "max"),
]

# Deduplicate symbols within each group before building jobs
def dedupe(symbols):
    seen = set()
    out = []
    for entry in symbols:
        if entry[0] not in seen:
            seen.add(entry[0])
            out.append(entry)
    return out

ALL_STOCK_SYMBOLS = dedupe(
    INDEX_ETFS + SECTOR_ETFS + COMMODITY_BOND_ETFS +
    LEVERAGED_ETFS + TECH_STOCKS + BLUE_CHIPS + MOMENTUM_STOCKS
)

JOBS = (
    make_jobs(ALL_STOCK_SYMBOLS, STOCK_TIMEFRAMES) +
    make_jobs(dedupe(FUTURES),   FUTURES_TIMEFRAMES) +
    make_jobs(dedupe(CRYPTO),    CRYPTO_TIMEFRAMES) +
    make_jobs(dedupe(FOREX),     FOREX_TIMEFRAMES)
)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print(f"Connecting to TimescaleDB at {DB_CONFIG['host']}:{DB_CONFIG['port']} "
          f"(db={DB_CONFIG['dbname']}) ...", end=" ")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        conn.close()
        print("OK\n")
    except Exception as e:
        sys.exit(f"FAILED: {e}")

    total = len(JOBS)
    total_inserted = 0

    stocks = len(ALL_STOCK_SYMBOLS)
    futures = len(dedupe(FUTURES))
    cryptos = len(dedupe(CRYPTO))
    forexs  = len(dedupe(FOREX))
    print(f"Total jobs: {total}  "
          f"({stocks} stocks/ETFs × 3tf, "
          f"{futures} futures × 3tf, "
          f"{cryptos} crypto × 3tf, "
          f"{forexs} forex × 3tf)\n")

    for i, (symbol, exchange, timeframe, yf_interval, period) in enumerate(JOBS, start=1):
        total_inserted += load(symbol, exchange, timeframe, yf_interval, period, i, total)
        time.sleep(0.8)  # respect Yahoo Finance rate limits

    print(f"\n{'─' * 60}")
    print(f"Done. Total rows inserted: {total_inserted:,}")


if __name__ == "__main__":
    main()
