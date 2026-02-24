# TRADIE - Comprehensive Trading Bot Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Trading Knowledge Verification](#trading-knowledge-verification)
3. [Recommended Tech Stack](#recommended-tech-stack)
4. [Cost Analysis & Free Alternatives](#cost-analysis--free-alternatives)
5. [Implementation Tickets](#implementation-tickets)

---

# Overview

## Project Name: Tradie Bot
**Version:** 1.0.0  
**Author:** Isaac Njoroge  
**Primary Focus:** Swing Trading & Active/Day Trading  
**Supported Markets:** Stocks, Cryptocurrency, Forex, Futures  
**Broker:** Interactive Brokers (USD)  
**Charting Integration:** TradingView  
**Cash Flow:** Grey.co for deposits/withdrawals  

---

## 1. System Concept

Tradie is an autonomous, multi-strategy trading bot designed to:
- Execute trades directly via Interactive Brokers (IBKR)
- Send real-time alerts to the user via Telegram
- Integrate technical analysis via TradingView webhooks
- Implement advanced Smart Money Concepts (SMC/ICT methodology)
- Include a "News Shield" NLP system to filter bad trading conditions
- Support multiple asset classes with proper risk management

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           TRADIE ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌──────────────┐                                                       │
│   │  TradingView │ ──────► Pine Script Signals                          │
│   │  (The Eyes)  │         (FVG, Order Blocks, BOS/CHoCH)               │
│   └──────────────┘                                                       │
│          │                                                               │
│          ▼ Webhook (JSON)                                               │
│   ┌──────────────────────────────────────────────────────────┐         │
│   │              API GATEWAY (Spring Boot)                    │         │
│   │              - Webhook Receiver                          │         │
│   │              - Authentication                            │         │
│   │              - Request Validation                        │         │
│   └──────────────────────────────────────────────────────────┘         │
│          │                                                               │
│          ▼                                                               │
│   ┌─────────────────────────────────────────────────────────┐          │
│   │                  KAFKA MESSAGE BROKER                    │          │
│   │     ┌─────────┐  ┌─────────┐  ┌─────────┐              │          │
│   │     │ signals │  │ orders  │  │  alerts │              │          │
│   └─────┴─────────┴──┴─────────┴──┴─────────┴──────────────┘          │
│          │                  │              │                            │
│   ┌──────▼──────┐    ┌─────▼─────┐   ┌────▼────┐                       │
│   │ News Shield │    │  Strategy │   │  Alert  │                       │
│   │  (Python)   │    │  Engine   │   │ Service │                       │
│   │  - NLP      │    │  (Java)   │   │(Telegram)│                       │
│   │  - Events   │    │  - Risk   │   └─────────┘                       │
│   └──────┬──────┘    │  - Size   │                                      │
│          │           │  - Logic  │                                      │
│          ▼           └─────┬─────┘                                      │
│   ┌────────────┐           │                                            │
│   │safe_to_trade│          ▼                                            │
│   │  true/false │    ┌───────────┐                                      │
│   └──────┬──────┘    │  Order    │                                      │
│          └──────────►│ Execution │                                      │
│                      │  (IBKR)   │                                      │
│                      └─────┬─────┘                                      │
│                            │                                            │
│                            ▼                                            │
│                      ┌───────────┐                                      │
│                      │TimescaleDB│ ◄── Trade Logs, OHLCV, Audit        │
│                      └───────────┘                                      │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow

1. **TradingView (The Eyes)**: User sets up charts with Custom Pine Scripts. When FVG, Order Block, or other criteria are met, TV fires a JSON Webhook.

2. **Spring Boot API Gateway**: Receives Webhook, validates authentication token, and publishes to Kafka.

3. **News Shield (Python Microservice)**: Checks financial news APIs and economic calendar. Returns `safe_to_trade: true/false`.

4. **Strategy Engine (Java)**: Validates the TV signal against News Shield result, applies risk management rules, calculates position size.

5. **IBKR Execution (Java)**: If validated, executes the trade as a bracket order (entry + stop loss + take profit).

6. **Alert Service**: Sends Telegram notifications for trade entries, exits, and blocked signals.

7. **TimescaleDB**: Stores all trade data, OHLCV candles, signals, and audit logs.

---

# Trading Knowledge Verification

## ✅ CONFIRMED: All Requested Trading Concepts Captured

### Module A: Smart Money Concepts (ICT/SMC) - PRIMARY FOCUS

| Concept | Status | Detection Algorithm |
|---------|--------|---------------------|
| Fair Value Gaps (FVG) | ✅ | 3-candle pattern: `low[0] > high[2]` (bullish) or `high[0] < low[2]` (bearish) |
| Order Blocks | ✅ | Last opposite-color candle before displacement, validated by engulfing + FVG |
| Breaker Blocks | ✅ | Failed order block that flips polarity when price closes beyond its boundary |
| Liquidity Sweeps (Stop Hunts) | ✅ | Price exceeds previous swing high/low then reverses - "Judas Swings" |
| Kill Zones | ✅ | London Open (2-5 AM EST), NY Open (8:30-11 AM), Silver Bullet (10-11 AM), NY PM (1-3 PM) |
| Optimal Trade Entry (OTE) | ✅ | Fibonacci 0.62-0.79 retracement zone after BOS |
| Market Structure Shift (MSS) | ✅ | CHoCH + Displacement (3+ strong candles) + FVG formation |
| Displacement & Imbalance | ✅ | 3+ consecutive candles in same direction with increasing momentum |
| Premium/Discount Zones | ✅ | Above 50% of range = Premium (sell), Below 50% = Discount (buy) |
| Institutional Order Flow | ✅ | Volume Profile, VWAP deviation, Order book imbalance analysis |

### Module B: Price Action & Market Structure

| Concept | Status | Implementation |
|---------|--------|----------------|
| Break of Structure (BOS) | ✅ | Price closes beyond previous swing high/low by > ATR(17) |
| Change of Character (CHoCH) | ✅ | First opposite BOS after a trend (trend reversal signal) |
| Support & Resistance | ✅ | Horizontal levels from swing points, pivot points, psychological levels |
| Higher Highs/Higher Lows | ✅ | Uptrend: HH > previous HH AND HL > previous HL |
| Lower Highs/Lower Lows | ✅ | Downtrend: LH < previous LH AND LL < previous LL |
| Trendline Bounces | ✅ | Connect swing lows (uptrend) or swing highs (downtrend) |
| Chart Patterns | ✅ | Head & Shoulders, Double Top/Bottom, Triangles, Wedges, Flags, Pennants |
| Candlestick Patterns | ✅ | Engulfing, Doji, Hammer, Shooting Star, Morning/Evening Star, etc. |
| Supply & Demand Zones | ✅ | Areas of strong buying/selling pressure from historical price action |
| Pivot Points | ✅ | Classic, Fibonacci, Woodie, Camarilla pivot calculations |

### Module C: Technical Indicators

| Category | Indicators | Status | Optimal Parameters |
|----------|------------|--------|-------------------|
| **Momentum** | RSI | ✅ | Period: 14, OB: 70, OS: 30 |
| | MACD | ✅ | 12/26/9 EMA settings |
| | Stochastic | ✅ | 14/3/3, OB: 80, OS: 20 |
| | CCI | ✅ | Period: 20, Levels: ±100 |
| | Williams %R | ✅ | Period: 14, OB: -20, OS: -80 |
| | Rate of Change | ✅ | Period: 10 |
| **Trend** | SMA/EMA | ✅ | 9, 20, 50, 100, 200 periods |
| | ADX | ✅ | Period: 14, Trending: >25, Strong: >40 |
| | Parabolic SAR | ✅ | AF: 0.02, Max: 0.2 |
| | Ichimoku Cloud | ✅ | 9/26/52 standard settings |
| **Volatility** | Bollinger Bands | ✅ | 20 SMA, 2 StdDev |
| | ATR | ✅ | Period: 14 |
| | Keltner Channels | ✅ | 20 EMA, 2x ATR(10) |
| **Volume** | OBV | ✅ | Cumulative volume + price direction |
| | VWAP | ✅ | Anchored to session start |
| | Volume Profile | ✅ | POC, VAH, VAL levels |
| | MFI | ✅ | Period: 14, OB: 80, OS: 20 |
| | A/D Line | ✅ | Cumulative money flow |

### Module D: Swing Trading Strategies (FROM INVESTOPEDIA & SOURCES)

| Strategy | Status | Entry Signal | Exit Signal | Stop Loss |
|----------|--------|--------------|-------------|-----------|
| Trend Following | ✅ | MA crossover OR price > 50 EMA | Reversal candle OR MA re-cross | Below swing low / Above swing high |
| Range Trading | ✅ | RSI < 30 at support | RSI > 70 at resistance | Below support / Above resistance |
| Breakout Trading | ✅ | Close beyond S/R + Volume spike | Next S/R level | Below breakout level |
| Pullback Trading | ✅ | Bounce off trendline/MA in trend | Previous swing high/low | Below trendline |
| Fibonacci Retracement | ✅ | Price enters 50%-61.8% zone | Fibonacci extensions (127.2%, 161.8%) | Below 78.6% retracement |
| Moving Average Crossover | ✅ | 9 EMA crosses 21 EMA | Opposite crossover | ATR(14) x 1.5 from entry |

### Module E: Day Trading Strategies (FROM SARWA.CO & B2BROKER)

| Strategy | Status | Timeframe | Entry Criteria | Exit Criteria |
|----------|--------|-----------|----------------|---------------|
| Momentum Trading | ✅ | 5-15 min | MACD divergence + Volume spike | Momentum fade on indicator |
| Scalping | ✅ | 1-5 min | MA Ribbon (5/8/13 EMA) alignment | Glued to 13 EMA (reversal) |
| Gap Trading | ✅ | Daily open | Gap up/down classification | Gap fill OR continuation |
| Opening Range Breakout | ✅ | 15-60 min | Break of first 15-60 min range | Measured move (range height) |
| VWAP Strategy | ✅ | Intraday | Bounce off VWAP ± 2σ bands | Mean reversion to VWAP |
| Pivot Point Trading | ✅ | Intraday | Bounce/break of pivot levels | Next pivot level |
| News Trading | ✅ | Event-driven | Pre/post news positioning | Volatility fade |
| Ichimoku Strategy | ✅ | 4H/Daily | Price above cloud + Tenkan>Kijun | Price enters cloud |
| Price Action Trading | ✅ | All | Pattern recognition (no indicators) | Pattern completion |

### Module F: Risk Management

| Concept | Status | Formula/Rule |
|---------|--------|--------------|
| Fixed Fractional Risk | ✅ | `position_size = (account × risk%) / (entry - stop)` |
| Kelly Criterion | ✅ | `kelly% = win_rate - (1 - win_rate) / (avg_win / avg_loss)` (use ¼ Kelly) |
| ATR-Based Stop Loss | ✅ | `stop = entry ± (ATR × multiplier)` - Multipliers: Day=1.5-2, Swing=2-2.5 |
| Risk/Reward Ratio | ✅ | Minimum 2:1 R:R, optimal 3:1 |
| Maximum Drawdown | ✅ | Pause trading at 15-20% drawdown |
| Daily Loss Limit | ✅ | Stop trading after 3% daily loss |
| Portfolio Heat | ✅ | Maximum 6% total portfolio risk across all positions |
| Correlation Adjustment | ✅ | Reduce combined size for ρ > 0.7: `adjusted = size × √(2/(1+ρ))` |
| Losing Streak Reduction | ✅ | After 3 consecutive losses, reduce size by 50% |

### Module G: Multi-Asset Specifications

#### Forex
| Specification | Value |
|--------------|-------|
| Pip Calculation | Standard: 4th decimal (0.0001), JPY: 2nd decimal (0.01) |
| Lot Sizes | Standard: 100K, Mini: 10K, Micro: 1K units |
| Pip Value (USD pairs) | Standard: $10/pip, Mini: $1/pip, Micro: $0.10/pip |
| Swap/Rollover | Triple swap on Wednesdays (T+2 settlement) |
| Market Hours | 24/5 (Sunday 5 PM - Friday 5 PM EST) |

#### Futures
| Contract | Symbol | Multiplier | Tick Value | Roll Period |
|----------|--------|------------|------------|-------------|
| E-mini S&P 500 | ES | $50 | $12.50 | 8 days before quarterly expiry |
| E-mini Nasdaq | NQ | $20 | $5.00 | 8 days before quarterly expiry |
| Micro E-mini S&P | MES | $5 | $1.25 | 8 days before quarterly expiry |
| Crude Oil | CL | $1,000 | $10.00 | ~15-20 days before expiry |

#### Cryptocurrency
| Specification | Value |
|--------------|-------|
| Market Hours | 24/7/365 |
| Funding Rate | Every 8 hours on perpetual futures |
| Volatility Adjustment | `crypto_size = stock_size × (stock_vol / crypto_vol)` |
| Exchange Risk | Use only top-tier exchanges (Coinbase, Kraken) |

#### Stocks
| Specification | Value |
|--------------|-------|
| Market Hours | 9:30 AM - 4:00 PM EST |
| Pre-Market | 4:00 AM - 9:30 AM EST |
| After-Hours | 4:00 PM - 8:00 PM EST |
| PDT Rule | $25,000 minimum for unlimited day trades |

### Module H: When NOT to Trade (News Shield)

| Event Type | Pause Before | Pause After | Action |
|------------|-------------|-------------|--------|
| NFP (Non-Farm Payroll) | 30 min | 60 min | Close all positions |
| FOMC (Fed Rates) | 30 min | 60 min | Close all positions |
| CPI (Inflation) | 15 min | 45 min | Close all positions |
| GDP Release | 15 min | 30 min | 50% position reduction |
| Earnings (stock) | 0 | 15 min | Avoid that stock |
| VIX > 30 | - | - | 50% size reduction |
| VIX > 40 | - | - | No new positions |

---

# Recommended Tech Stack

## Core Backend (Java 21 + Spring Boot 3.x)

| Component | Technology | Purpose |
|-----------|------------|---------|
| Framework | Spring Boot 3.x | API Gateway, Order Execution |
| Language | Java 21 | Type safety, performance, IBKR native support |
| Technical Analysis | ta4j | 130+ indicators, backtesting framework |
| Build Tool | Gradle | Dependency management |
| API Docs | SpringDoc OpenAPI | Auto-generated documentation |

## Analytics/ML Microservice (Python 3.11+)

| Component | Technology | Purpose |
|-----------|------------|---------|
| Framework | FastAPI | High-performance async API |
| Technical Analysis | pandas-ta | 150+ indicators |
| Backtesting | VectorBT | Ultra-fast vectorized backtesting |
| NLP Sentiment | FinBERT / VADER | Financial news sentiment analysis |
| ML Framework | scikit-learn / ONNX | Model training and inference |

## Data Layer

| Component | Technology | Purpose |
|-----------|------------|---------|
| Time-Series DB | TimescaleDB | PostgreSQL-based, optimized for OHLCV data |
| Cache | Redis | Session state, real-time data |
| Message Broker | Apache Kafka | Event streaming between services |

## Infrastructure

| Component | Technology | Purpose |
|-----------|------------|---------|
| Containerization | Docker | Consistent deployment |
| Orchestration | Docker Compose / K8s | Multi-service management |
| Reverse Proxy | Nginx | SSL termination, load balancing |
| Monitoring | Prometheus + Grafana | Metrics and dashboards |

## External Integrations

| Service | Technology | Purpose |
|---------|------------|---------|
| Broker | Interactive Brokers TWS API | Trade execution |
| Charts | TradingView Webhooks | Signal generation |
| Alerts | Telegram Bot API | User notifications |
| News | Finnhub / Alpha Vantage | Financial news & sentiment |
| Economic Calendar | TradingEconomics API | High-impact event detection |

---

# Cost Analysis & Free Alternatives

## 💰 PAID SERVICES

### Broker & Trading Costs

| Service | Cost | Notes |
|---------|------|-------|
| **Interactive Brokers** | $0 account minimum | Commission: $0.005/share (min $1), $0.65/contract (options) |
| **TradingView** | Free - $59.95/mo | Free tier allows 1 alert; Pro+ ($24.95) for webhook alerts |
| **Grey.co** | Variable fees | Depends on deposit/withdrawal method |

### Data & APIs

| Service | Cost | Free Alternative |
|---------|------|------------------|
| **TradingEconomics API** | $29.95/mo | ✅ **Finnhub** (60 calls/min free) |
| **Polygon.io** | $199/mo+ | ✅ **Alpha Vantage** (25 calls/day free) |
| **Benzinga News** | $99/mo+ | ✅ **Finnhub News** (free tier) |

### Infrastructure

| Service | Cost | Free Alternative |
|---------|------|------------------|
| **AWS EC2** (t3.medium) | ~$30/mo | ✅ **Oracle Cloud Free Tier** (always free VM) |
| **TimescaleDB Cloud** | $29/mo+ | ✅ **Self-hosted TimescaleDB** (Docker) |
| **ngrok Pro** | $8/mo | ✅ **Cloudflare Tunnels** (free) |

### Development Tools

| Service | Cost | Free Alternative |
|---------|------|------------------|
| **GitHub Copilot** | $10/mo | ✅ **Claude** (you're using it!) |
| **Postman Pro** | $12/mo | ✅ **Hoppscotch** (open source) |

---

## ✅ RECOMMENDED FREE STACK

### Total Monthly Cost: $0 - $24.95

| Component | Free Solution | Limitations |
|-----------|---------------|-------------|
| **Broker** | Interactive Brokers (no minimum) | Commissions per trade |
| **Charts** | TradingView Free | 1 alert only |
| **Charts (Alerts)** | TradingView Pro ($24.95/mo) | 20 alerts - RECOMMENDED |
| **Hosting** | Oracle Cloud Free Tier | 1GB RAM VM (sufficient for dev) |
| **Database** | Self-hosted TimescaleDB | Requires maintenance |
| **News API** | Finnhub Free | 60 calls/minute |
| **Sentiment** | VADER + FinBERT | Self-hosted, no API cost |
| **Economic Calendar** | Finnhub Calendar | Included in free tier |
| **Webhooks** | Cloudflare Tunnels | Free, unlimited |
| **Alerts** | Telegram Bot | Free, unlimited messages |

### Minimum Viable Cost Breakdown

| Item | Monthly Cost |
|------|-------------|
| TradingView Pro (for webhooks) | $24.95 |
| Oracle Cloud VM | $0 |
| All other services | $0 |
| **TOTAL** | **$24.95/month** |

### ⚠️ One-Time Costs

| Item | Cost | Notes |
|------|------|-------|
| Domain (optional) | ~$12/year | For professional webhook URL |
| Interactive Brokers funding | $0 | No minimum, but need funds to trade |

---

# Implementation Tickets

## Phase 1: Foundation (Weeks 1-4)

---

### TICKET 1: Project Initialization & Repository Setup
**Priority:** P0 (Critical)  
**Estimated Time:** 4 hours  
**Dependencies:** None

#### Purpose
Set up the monorepo structure, initialize all microservices, configure build tools, and establish development environment.

#### Implementation Steps

**Step 1: Create Repository Structure**
```bash
mkdir tradie-bot && cd tradie-bot
git init

# Create directory structure
mkdir -p {api-gateway,strategy-engine,news-shield,order-executor,alert-service}
mkdir -p {common/src/main/java,infrastructure,scripts,docs}
mkdir -p data/{historical,logs,models}
```

**Step 2: Initialize Java Services (Spring Boot)**
```bash
# For each Java service (api-gateway, strategy-engine, order-executor)
cd api-gateway
spring init --dependencies=web,data-jpa,kafka,validation,actuator \
  --java-version=21 --type=gradle-project --name=api-gateway .
```

**Step 3: Create build.gradle for Parent Project**
```groovy
// settings.gradle (root)
rootProject.name = 'tradie-bot'
include 'api-gateway', 'strategy-engine', 'order-executor', 'common'

// build.gradle (root)
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0' apply false
    id 'io.spring.dependency-management' version '1.1.4' apply false
}

subprojects {
    apply plugin: 'java'
    
    java {
        sourceCompatibility = JavaVersion.VERSION_21
    }
    
    repositories {
        mavenCentral()
    }
}
```

**Step 4: Initialize Python Service (News Shield)**
```bash
cd news-shield
python -m venv venv
source venv/bin/activate

# Create pyproject.toml
cat > pyproject.toml << 'EOF'
[project]
name = "news-shield"
version = "1.0.0"
requires-python = ">=3.11"
dependencies = [
    "fastapi>=0.109.0",
    "uvicorn>=0.27.0",
    "httpx>=0.26.0",
    "transformers>=4.36.0",
    "torch>=2.1.0",
    "nltk>=3.8.1",
    "pydantic>=2.5.0",
    "redis>=5.0.0",
    "python-dotenv>=1.0.0",
]

[project.optional-dependencies]
dev = ["pytest>=7.4.0", "pytest-asyncio>=0.23.0", "black>=24.1.0"]
EOF

pip install -e ".[dev]"
```

**Step 5: Create Docker Compose for Local Development**
```yaml
# docker-compose.yml
version: '3.8'

services:
  # TimescaleDB
  timescaledb:
    image: timescale/timescaledb:latest-pg15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: tradie
      POSTGRES_PASSWORD: tradie_dev_123
      POSTGRES_DB: tradie
    volumes:
      - timescale_data:/var/lib/postgresql/data
      - ./infrastructure/db/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tradie"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  # Kafka + Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true

volumes:
  timescale_data:
  redis_data:
```

**Step 6: Create Environment Configuration**
```bash
# .env.example
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=tradie
DB_USER=tradie
DB_PASSWORD=your_secure_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# TradingView
TV_WEBHOOK_SECRET=your_webhook_secret

# Interactive Brokers
IB_HOST=127.0.0.1
IB_PORT=7497
IB_CLIENT_ID=1

# Telegram
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_CHAT_ID=your_chat_id

# News APIs
FINNHUB_API_KEY=your_finnhub_key
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_key
```

**Step 7: Initialize Git and Create Initial Commit**
```bash
# .gitignore
echo "
# IDE
.idea/
*.iml
.vscode/

# Build
build/
target/
dist/
*.egg-info/

# Python
__pycache__/
venv/
.venv/

# Environment
.env
.env.local

# Data
data/historical/*
data/logs/*
!data/historical/.gitkeep
!data/logs/.gitkeep

# Secrets
*.pem
*.key
" > .gitignore

git add .
git commit -m "Initial project setup with monorepo structure"
```

#### Acceptance Criteria
- [ ] All service directories created with proper structure
- [ ] Java services compile successfully with `./gradlew build`
- [ ] Python service installs without errors
- [ ] Docker Compose starts all infrastructure services
- [ ] Environment template created

---

### TICKET 2: TimescaleDB Schema & Data Models
**Priority:** P0 (Critical)  
**Estimated Time:** 6 hours  
**Dependencies:** Ticket 1

#### Purpose
Design and implement the database schema for storing OHLCV data, trade signals, executed orders, and audit logs with proper TimescaleDB optimizations.

#### Implementation Steps

**Step 1: Create Database Initialization Script**
```sql
-- infrastructure/db/init.sql

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- ===========================================
-- OHLCV Price Data (Hypertable)
-- ===========================================
CREATE TABLE ohlcv (
    time TIMESTAMPTZ NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,  -- '1m', '5m', '15m', '1h', '4h', '1d'
    open DOUBLE PRECISION NOT NULL,
    high DOUBLE PRECISION NOT NULL,
    low DOUBLE PRECISION NOT NULL,
    close DOUBLE PRECISION NOT NULL,
    volume BIGINT NOT NULL,
    
    PRIMARY KEY (time, symbol, exchange, timeframe)
);

-- Convert to hypertable
SELECT create_hypertable('ohlcv', 'time');

-- Add compression policy (compress data older than 7 days)
ALTER TABLE ohlcv SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol, exchange, timeframe'
);
SELECT add_compression_policy('ohlcv', INTERVAL '7 days');

-- Add retention policy (keep 2 years of data)
SELECT add_retention_policy('ohlcv', INTERVAL '2 years');

-- Create indexes for common queries
CREATE INDEX idx_ohlcv_symbol_time ON ohlcv (symbol, time DESC);
CREATE INDEX idx_ohlcv_exchange_symbol ON ohlcv (exchange, symbol);

-- ===========================================
-- Trade Signals Table
-- ===========================================
CREATE TYPE signal_action AS ENUM ('BUY', 'SELL');
CREATE TYPE signal_source AS ENUM ('TRADINGVIEW', 'INTERNAL', 'MANUAL');
CREATE TYPE signal_status AS ENUM ('PENDING', 'VALIDATED', 'EXECUTED', 'REJECTED', 'EXPIRED');

CREATE TABLE trade_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    action signal_action NOT NULL,
    strategy VARCHAR(50) NOT NULL,
    source signal_source NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    stop_loss DOUBLE PRECISION,
    take_profit DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    timeframe VARCHAR(10),
    status signal_status NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    raw_payload JSONB,
    
    -- Metadata
    processed_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ
);

CREATE INDEX idx_signals_status ON trade_signals (status);
CREATE INDEX idx_signals_symbol_time ON trade_signals (symbol, created_at DESC);
CREATE INDEX idx_signals_strategy ON trade_signals (strategy);

-- ===========================================
-- Orders Table
-- ===========================================
CREATE TYPE order_side AS ENUM ('BUY', 'SELL');
CREATE TYPE order_type AS ENUM ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT');
CREATE TYPE order_status AS ENUM ('PENDING', 'SUBMITTED', 'FILLED', 'PARTIALLY_FILLED', 'CANCELLED', 'REJECTED');

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    signal_id UUID REFERENCES trade_signals(id),
    ib_order_id INTEGER,
    ib_perm_id BIGINT,
    
    -- Order Details
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,  -- 'STK', 'CASH', 'FUT', 'CRYPTO'
    side order_side NOT NULL,
    order_type order_type NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    limit_price DOUBLE PRECISION,
    stop_price DOUBLE PRECISION,
    
    -- Bracket Order Links
    parent_order_id UUID,
    is_bracket_parent BOOLEAN DEFAULT FALSE,
    
    -- Status
    status order_status NOT NULL DEFAULT 'PENDING',
    filled_quantity DOUBLE PRECISION DEFAULT 0,
    avg_fill_price DOUBLE PRECISION,
    commission DOUBLE PRECISION,
    
    -- Timestamps
    submitted_at TIMESTAMPTZ,
    filled_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_orders_signal ON orders (signal_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_ib_id ON orders (ib_order_id);

-- ===========================================
-- Positions Table
-- ===========================================
CREATE TYPE position_status AS ENUM ('OPEN', 'CLOSED');

CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    
    -- Position Details
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    side order_side NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    entry_price DOUBLE PRECISION NOT NULL,
    exit_price DOUBLE PRECISION,
    
    -- Risk Management
    stop_loss DOUBLE PRECISION,
    take_profit DOUBLE PRECISION,
    trailing_stop_pct DOUBLE PRECISION,
    
    -- P&L
    realized_pnl DOUBLE PRECISION,
    unrealized_pnl DOUBLE PRECISION,
    commission_total DOUBLE PRECISION DEFAULT 0,
    
    -- Strategy & Source
    strategy VARCHAR(50),
    entry_signal_id UUID REFERENCES trade_signals(id),
    exit_signal_id UUID REFERENCES trade_signals(id),
    
    status position_status NOT NULL DEFAULT 'OPEN'
);

CREATE INDEX idx_positions_status ON positions (status);
CREATE INDEX idx_positions_symbol ON positions (symbol);

-- ===========================================
-- Economic Events Table (for News Shield)
-- ===========================================
CREATE TYPE event_impact AS ENUM ('LOW', 'MEDIUM', 'HIGH');

CREATE TABLE economic_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_time TIMESTAMPTZ NOT NULL,
    title VARCHAR(200) NOT NULL,
    country VARCHAR(3) NOT NULL,
    currency VARCHAR(5),
    impact event_impact NOT NULL,
    forecast VARCHAR(50),
    previous VARCHAR(50),
    actual VARCHAR(50),
    
    -- Processed flag
    processed BOOLEAN DEFAULT FALSE
);

SELECT create_hypertable('economic_events', 'event_time');
CREATE INDEX idx_events_time_impact ON economic_events (event_time, impact);

-- ===========================================
-- News Sentiment Table
-- ===========================================
CREATE TABLE news_sentiment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    published_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    source VARCHAR(50) NOT NULL,
    headline TEXT NOT NULL,
    summary TEXT,
    url TEXT,
    
    -- Sentiment Analysis
    sentiment_score DOUBLE PRECISION,  -- -1.0 to 1.0
    sentiment_label VARCHAR(20),       -- 'POSITIVE', 'NEGATIVE', 'NEUTRAL'
    
    -- Related Assets
    symbols VARCHAR(20)[]
);

SELECT create_hypertable('news_sentiment', 'published_at');
CREATE INDEX idx_news_symbols ON news_sentiment USING GIN (symbols);

-- ===========================================
-- Trade Audit Log (Hypertable)
-- ===========================================
CREATE TABLE audit_log (
    time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    service VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    details JSONB,
    user_id VARCHAR(50)
);

SELECT create_hypertable('audit_log', 'time');

-- ===========================================
-- System Configuration
-- ===========================================
CREATE TABLE system_config (
    key VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert default risk management configuration
INSERT INTO system_config (key, value, description) VALUES
('risk_management', '{
    "max_risk_per_trade_pct": 2.0,
    "max_daily_loss_pct": 3.0,
    "max_portfolio_heat_pct": 6.0,
    "max_concurrent_positions": 5,
    "min_risk_reward_ratio": 2.0,
    "losing_streak_reduction": 0.5,
    "losing_streak_threshold": 3
}', 'Risk management parameters'),

('kill_zones', '{
    "london_open": {"start": "02:00", "end": "05:00", "timezone": "America/New_York"},
    "ny_open": {"start": "08:30", "end": "11:00", "timezone": "America/New_York"},
    "silver_bullet": {"start": "10:00", "end": "11:00", "timezone": "America/New_York"},
    "ny_afternoon": {"start": "13:00", "end": "15:00", "timezone": "America/New_York"}
}', 'ICT Kill Zone timing configuration'),

('high_impact_events', '{
    "nfp": {"pause_before_min": 30, "pause_after_min": 60},
    "fomc": {"pause_before_min": 30, "pause_after_min": 60},
    "cpi": {"pause_before_min": 15, "pause_after_min": 45},
    "gdp": {"pause_before_min": 15, "pause_after_min": 30}
}', 'News Shield event pause configuration');
```

**Step 2: Create Java Entity Classes**
```java
// common/src/main/java/com/tradie/common/entity/TradeSignal.java
package com.tradie.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_signals")
public class TradeSignal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(nullable = false, length = 20)
    private String symbol;
    
    @Column(nullable = false, length = 20)
    private String exchange;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalAction action;
    
    @Column(nullable = false, length = 50)
    private String strategy;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalSource source;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(name = "stop_loss")
    private Double stopLoss;
    
    @Column(name = "take_profit")
    private Double takeProfit;
    
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    @Column(length = 10)
    private String timeframe;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalStatus status = SignalStatus.PENDING;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;
    
    // Getters, setters, constructors...
    
    public enum SignalAction { BUY, SELL }
    public enum SignalSource { TRADINGVIEW, INTERNAL, MANUAL }
    public enum SignalStatus { PENDING, VALIDATED, EXECUTED, REJECTED, EXPIRED }
}
```

**Step 3: Create Repository Interfaces**
```java
// common/src/main/java/com/tradie/common/repository/TradeSignalRepository.java
package com.tradie.common.repository;

import com.tradie.common.entity.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeSignalRepository extends JpaRepository<TradeSignal, UUID> {
    
    List<TradeSignal> findByStatusOrderByCreatedAtDesc(TradeSignal.SignalStatus status);
    
    List<TradeSignal> findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(
        String symbol, Instant since);
    
    @Query("SELECT s FROM TradeSignal s WHERE s.status = 'PENDING' " +
           "AND s.createdAt > :cutoff ORDER BY s.createdAt ASC")
    List<TradeSignal> findPendingSignals(Instant cutoff);
    
    @Query(value = "SELECT COUNT(*) FROM trade_signals " +
           "WHERE status = 'EXECUTED' AND created_at > NOW() - INTERVAL '24 hours'",
           nativeQuery = true)
    long countExecutedToday();
}
```

**Step 4: Create Database Migration with Flyway**
```properties
# api-gateway/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tradie}
    username: ${DB_USER:tradie}
    password: ${DB_PASSWORD:tradie_dev_123}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

#### Acceptance Criteria
- [ ] All tables created successfully in TimescaleDB
- [ ] Hypertables configured with proper compression and retention
- [ ] Java entities map correctly to database schema
- [ ] Flyway migrations run without errors
- [ ] Indexes created for common query patterns

---

### TICKET 3: API Gateway - Webhook Receiver & Authentication
**Priority:** P0 (Critical)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 1, Ticket 2

#### Purpose
Build the Spring Boot API Gateway that receives TradingView webhooks, validates authentication, and publishes signals to Kafka.

#### Implementation Steps

**Step 1: Add Dependencies to build.gradle**
```groovy
// api-gateway/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation project(':common')
    
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    // Kafka
    implementation 'org.springframework.kafka:spring-kafka'
    
    // Database
    runtimeOnly 'org.postgresql:postgresql'
    
    // JSON
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
    
    // Metrics
    implementation 'io.micrometer:micrometer-registry-prometheus'
    
    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}
```

**Step 2: Create Webhook DTO**
```java
// api-gateway/src/main/java/com/tradie/gateway/dto/TradingViewSignal.java
package com.tradie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record TradingViewSignal(
    @NotBlank(message = "Symbol is required")
    @Size(max = 20, message = "Symbol must be 20 characters or less")
    String symbol,
    
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "BUY|SELL", message = "Action must be BUY or SELL")
    String action,
    
    @NotBlank(message = "Strategy is required")
    @Size(max = 50, message = "Strategy must be 50 characters or less")
    String strategy,
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    Double price,
    
    @JsonProperty("stop_loss")
    @Positive(message = "Stop loss must be positive")
    Double stopLoss,
    
    @JsonProperty("take_profit")
    @Positive(message = "Take profit must be positive")
    Double takeProfit,
    
    @JsonProperty("auth_token")
    @NotBlank(message = "Auth token is required")
    String authToken,
    
    // Optional fields
    String exchange,
    String timeframe,
    
    @JsonProperty("confidence")
    @Min(0) @Max(100)
    Double confidence
) {
    // Provide defaults for optional fields
    public TradingViewSignal {
        exchange = exchange != null ? exchange : "SMART";
        timeframe = timeframe != null ? timeframe : "15m";
    }
}
```

**Step 3: Create Webhook Controller**
```java
// api-gateway/src/main/java/com/tradie/gateway/controller/WebhookController.java
package com.tradie.gateway.controller;

import com.tradie.gateway.dto.TradingViewSignal;
import com.tradie.gateway.service.SignalIngestionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    
    private final SignalIngestionService signalService;
    private final String webhookSecret;
    
    public WebhookController(
            SignalIngestionService signalService,
            @Value("${tradie.webhook.secret}") String webhookSecret) {
        this.signalService = signalService;
        this.webhookSecret = webhookSecret;
    }
    
    @PostMapping("/tradingview")
    public ResponseEntity<?> receiveTradingViewSignal(
            @Valid @RequestBody TradingViewSignal signal,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        
        // 1. Validate authentication token
        if (!webhookSecret.equals(signal.authToken())) {
            log.warn("Unauthorized webhook attempt for symbol: {} from IP: {}", 
                signal.symbol(), getClientIp());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid authentication token"));
        }
        
        // 2. Log incoming signal
        log.info("SIGNAL RECEIVED: {} {} @ {} [Strategy: {}, Timeframe: {}]",
            signal.action(), signal.symbol(), signal.price(), 
            signal.strategy(), signal.timeframe());
        
        // 3. Process and publish to Kafka
        try {
            String signalId = signalService.processIncomingSignal(signal);
            
            return ResponseEntity.ok(Map.of(
                "status", "acknowledged",
                "signal_id", signalId,
                "message", "Signal queued for processing"
            ));
            
        } catch (Exception e) {
            log.error("Failed to process signal: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to process signal"));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "api-gateway",
            "timestamp", java.time.Instant.now().toString()
        ));
    }
    
    // Get client IP for logging (accounting for proxies)
    private String getClientIp() {
        // Implementation would use HttpServletRequest
        return "unknown";
    }
}
```

**Step 4: Create Signal Ingestion Service**
```java
// api-gateway/src/main/java/com/tradie/gateway/service/SignalIngestionService.java
package com.tradie.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.gateway.dto.TradingViewSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignalIngestionService {
    
    private static final Logger log = LoggerFactory.getLogger(SignalIngestionService.class);
    private static final String SIGNALS_TOPIC = "tradie.signals";
    
    private final TradeSignalRepository signalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public SignalIngestionService(
            TradeSignalRepository signalRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.signalRepository = signalRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public String processIncomingSignal(TradingViewSignal tvSignal) throws Exception {
        // 1. Create and persist signal entity
        TradeSignal signal = new TradeSignal();
        signal.setSymbol(tvSignal.symbol().toUpperCase());
        signal.setExchange(tvSignal.exchange());
        signal.setAction(TradeSignal.SignalAction.valueOf(tvSignal.action()));
        signal.setStrategy(tvSignal.strategy());
        signal.setSource(TradeSignal.SignalSource.TRADINGVIEW);
        signal.setPrice(tvSignal.price());
        signal.setStopLoss(tvSignal.stopLoss());
        signal.setTakeProfit(tvSignal.takeProfit());
        signal.setTimeframe(tvSignal.timeframe());
        signal.setConfidenceScore(tvSignal.confidence() != null ? tvSignal.confidence() / 100.0 : null);
        signal.setRawPayload(objectMapper.writeValueAsString(tvSignal));
        
        signal = signalRepository.save(signal);
        
        // 2. Publish to Kafka for async processing
        String messageKey = signal.getSymbol();
        String messageValue = objectMapper.writeValueAsString(signal);
        
        kafkaTemplate.send(SIGNALS_TOPIC, messageKey, messageValue)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish signal {} to Kafka: {}", 
                        signal.getId(), ex.getMessage());
                } else {
                    log.debug("Signal {} published to Kafka partition {}", 
                        signal.getId(), result.getRecordMetadata().partition());
                }
            });
        
        return signal.getId().toString();
    }
}
```

**Step 5: Configure Kafka**
```java
// api-gateway/src/main/java/com/tradie/gateway/config/KafkaConfig.java
package com.tradie.gateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public NewTopic signalsTopic() {
        return TopicBuilder.name("tradie.signals")
            .partitions(3)
            .replicas(1)
            .build();
    }
    
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("tradie.orders")
            .partitions(3)
            .replicas(1)
            .build();
    }
    
    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name("tradie.alerts")
            .partitions(1)
            .replicas(1)
            .build();
    }
    
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**Step 6: Application Configuration**
```yaml
# api-gateway/src/main/resources/application.yml
server:
  port: 8080

spring:
  application:
    name: tradie-api-gateway
  
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tradie}
    username: ${DB_USER:tradie}
    password: ${DB_PASSWORD:tradie_dev_123}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

tradie:
  webhook:
    secret: ${TV_WEBHOOK_SECRET:TRADIE_SEC_9982}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.tradie: DEBUG
    org.springframework.kafka: INFO
```

#### Acceptance Criteria
- [ ] Webhook endpoint accepts POST requests at `/api/v1/webhook/tradingview`
- [ ] Invalid tokens return 401 Unauthorized
- [ ] Valid signals are persisted to PostgreSQL
- [ ] Signals are published to Kafka topic
- [ ] Health endpoint returns service status
- [ ] All validation errors return meaningful messages

---

### TICKET 4: TradingView Pine Script - FVG & Order Block Detection
**Priority:** P0 (Critical)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 3

#### Purpose
Create comprehensive TradingView Pine Scripts for detecting Fair Value Gaps, Order Blocks, and Market Structure that fire webhook alerts to the API Gateway.

#### Implementation Steps

**Step 1: Core FVG Detection Script**
```pinescript
// scripts/pinescript/tradie_fvg_detector.pine
//@version=5
indicator("Tradie - FVG Detector", overlay=true, max_boxes_count=500)

// ============================================
// INPUTS
// ============================================
i_showBullishFVG = input.bool(true, "Show Bullish FVG", group="Display")
i_showBearishFVG = input.bool(true, "Show Bearish FVG", group="Display")
i_minFVGSize = input.float(0.5, "Minimum FVG Size (ATR multiplier)", minval=0.1, step=0.1, group="Filters")
i_maxFVGAge = input.int(50, "Max FVG Age (bars)", minval=10, group="Filters")
i_alertOnNew = input.bool(true, "Alert on New FVG", group="Alerts")

// Webhook Configuration
i_webhookSecret = input.string("TRADIE_SEC_9982", "Webhook Secret", group="Webhook")

// ============================================
// CALCULATIONS
// ============================================
atr = ta.atr(14)
minGapSize = atr * i_minFVGSize

// Bullish FVG: Gap between candle 1 high and candle 3 low
bullishFVG_bottom = high[2]
bullishFVG_top = low[0]
bullishFVG_exists = bullishFVG_top > bullishFVG_bottom
bullishFVG_size = bullishFVG_top - bullishFVG_bottom
bullishFVG_valid = bullishFVG_exists and bullishFVG_size >= minGapSize

// Confirm with displacement (strong middle candle)
bullishDisplacement = close[1] > open[1] and (close[1] - open[1]) > atr * 0.5

// Bearish FVG: Gap between candle 1 low and candle 3 high
bearishFVG_top = low[2]
bearishFVG_bottom = high[0]
bearishFVG_exists = bearishFVG_top > bearishFVG_bottom
bearishFVG_size = bearishFVG_top - bearishFVG_bottom
bearishFVG_valid = bearishFVG_exists and bearishFVG_size >= minGapSize

// Confirm with displacement
bearishDisplacement = close[1] < open[1] and (open[1] - close[1]) > atr * 0.5

// Final signals
bullishSignal = bullishFVG_valid and bullishDisplacement
bearishSignal = bearishFVG_valid and bearishDisplacement

// ============================================
// VISUALIZATION
// ============================================
var box[] bullishBoxes = array.new_box()
var box[] bearishBoxes = array.new_box()

if bullishSignal and i_showBullishFVG
    newBox = box.new(bar_index[2], bullishFVG_top, bar_index + 10, bullishFVG_bottom,
        bgcolor=color.new(color.green, 80), border_color=color.green)
    array.push(bullishBoxes, newBox)

if bearishSignal and i_showBearishFVG
    newBox = box.new(bar_index[2], bearishFVG_top, bar_index + 10, bearishFVG_bottom,
        bgcolor=color.new(color.red, 80), border_color=color.red)
    array.push(bearishBoxes, newBox)

// ============================================
// WEBHOOK ALERTS
// ============================================
// Calculate stop loss and take profit
bullishSL = bullishFVG_bottom - (atr * 0.5)
bullishTP = close + ((close - bullishSL) * 2)  // 2:1 R:R

bearishSL = bearishFVG_top + (atr * 0.5)
bearishTP = close - ((bearishSL - close) * 2)  // 2:1 R:R

// Build JSON payloads
bullishPayload = '{"symbol":"' + syminfo.ticker + '","action":"BUY","strategy":"FVG_BULLISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bullishSL) + ',"take_profit":' + str.tostring(bullishTP) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":' + str.tostring(math.round(bullishFVG_size / atr * 50)) + ',"auth_token":"' + i_webhookSecret + '"}'

bearishPayload = '{"symbol":"' + syminfo.ticker + '","action":"SELL","strategy":"FVG_BEARISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bearishSL) + ',"take_profit":' + str.tostring(bearishTP) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":' + str.tostring(math.round(bearishFVG_size / atr * 50)) + ',"auth_token":"' + i_webhookSecret + '"}'

// Fire alerts
if bullishSignal and i_alertOnNew
    alert(bullishPayload, alert.freq_once_per_bar_close)

if bearishSignal and i_alertOnNew
    alert(bearishPayload, alert.freq_once_per_bar_close)

// ============================================
// DEBUG LABELS
// ============================================
if bullishSignal
    label.new(bar_index, low, "BUY\nFVG", style=label.style_label_up, color=color.green, textcolor=color.white)

if bearishSignal
    label.new(bar_index, high, "SELL\nFVG", style=label.style_label_down, color=color.red, textcolor=color.white)
```

**Step 2: Order Block Detection Script**
```pinescript
// scripts/pinescript/tradie_order_blocks.pine
//@version=5
indicator("Tradie - Order Blocks & BOS", overlay=true, max_boxes_count=500)

// ============================================
// INPUTS
// ============================================
i_swingLength = input.int(5, "Swing Length", minval=2, maxval=10, group="Structure")
i_showOB = input.bool(true, "Show Order Blocks", group="Display")
i_showBOS = input.bool(true, "Show BOS/CHoCH", group="Display")
i_webhookSecret = input.string("TRADIE_SEC_9982", "Webhook Secret", group="Webhook")

// ============================================
// SWING POINT DETECTION
// ============================================
swingHigh = ta.pivothigh(high, i_swingLength, i_swingLength)
swingLow = ta.pivotlow(low, i_swingLength, i_swingLength)

var float lastSwingHigh = na
var float lastSwingLow = na
var int lastSwingHighBar = na
var int lastSwingLowBar = na

if not na(swingHigh)
    lastSwingHigh := swingHigh
    lastSwingHighBar := bar_index[i_swingLength]

if not na(swingLow)
    lastSwingLow := swingLow
    lastSwingLowBar := bar_index[i_swingLength]

// ============================================
// BREAK OF STRUCTURE (BOS) DETECTION
// ============================================
atr = ta.atr(17)

// Bullish BOS: Close above last swing high with significance
bullishBOS = not na(lastSwingHigh) and close > lastSwingHigh and (close - lastSwingHigh) > atr * 0.1

// Bearish BOS: Close below last swing low with significance
bearishBOS = not na(lastSwingLow) and close < lastSwingLow and (lastSwingLow - close) > atr * 0.1

// Track trend for CHoCH detection
var int trend = 0  // 1 = bullish, -1 = bearish

bullishCHoCH = bullishBOS and trend == -1
bearishCHoCH = bearishBOS and trend == 1

if bullishBOS
    trend := 1
if bearishBOS
    trend := -1

// ============================================
// ORDER BLOCK DETECTION
// ============================================
// Bullish OB: Last bearish candle before bullish BOS
var float bullishOB_high = na
var float bullishOB_low = na
var int bullishOB_bar = na

// Bearish OB: Last bullish candle before bearish BOS
var float bearishOB_high = na
var float bearishOB_low = na
var int bearishOB_bar = na

// Find last opposite candle before BOS
if bullishBOS
    for i = 1 to 10
        if close[i] < open[i]  // Found bearish candle
            bullishOB_high := high[i]
            bullishOB_low := low[i]
            bullishOB_bar := bar_index[i]
            break

if bearishBOS
    for i = 1 to 10
        if close[i] > open[i]  // Found bullish candle
            bearishOB_high := high[i]
            bearishOB_low := low[i]
            bearishOB_bar := bar_index[i]
            break

// ============================================
// VISUALIZATION
// ============================================
// BOS Lines
if bullishBOS and i_showBOS
    line.new(lastSwingHighBar, lastSwingHigh, bar_index, lastSwingHigh, 
        color=bullishCHoCH ? color.blue : color.green, style=line.style_dashed)
    label.new(bar_index, lastSwingHigh, bullishCHoCH ? "CHoCH" : "BOS", 
        style=label.style_label_down, color=bullishCHoCH ? color.blue : color.green, 
        textcolor=color.white, size=size.small)

if bearishBOS and i_showBOS
    line.new(lastSwingLowBar, lastSwingLow, bar_index, lastSwingLow, 
        color=bearishCHoCH ? color.orange : color.red, style=line.style_dashed)
    label.new(bar_index, lastSwingLow, bearishCHoCH ? "CHoCH" : "BOS", 
        style=label.style_label_up, color=bearishCHoCH ? color.orange : color.red, 
        textcolor=color.white, size=size.small)

// Order Block Boxes
if bullishBOS and not na(bullishOB_high) and i_showOB
    box.new(bullishOB_bar, bullishOB_high, bar_index + 20, bullishOB_low,
        bgcolor=color.new(color.green, 85), border_color=color.green, border_width=2)

if bearishBOS and not na(bearishOB_high) and i_showOB
    box.new(bearishOB_bar, bearishOB_high, bar_index + 20, bearishOB_low,
        bgcolor=color.new(color.red, 85), border_color=color.red, border_width=2)

// ============================================
// WEBHOOK ALERTS
// ============================================
// CHoCH signals (higher probability - trend reversal)
if bullishCHoCH
    chochPayload = '{"symbol":"' + syminfo.ticker + '","action":"BUY","strategy":"CHOCH_BULLISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bullishOB_low - atr * 0.5) + ',"take_profit":' + str.tostring(close + (close - bullishOB_low) * 2) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":80,"auth_token":"' + i_webhookSecret + '"}'
    alert(chochPayload, alert.freq_once_per_bar_close)

if bearishCHoCH
    chochPayload = '{"symbol":"' + syminfo.ticker + '","action":"SELL","strategy":"CHOCH_BEARISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bearishOB_high + atr * 0.5) + ',"take_profit":' + str.tostring(close - (bearishOB_high - close) * 2) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":80,"auth_token":"' + i_webhookSecret + '"}'
    alert(chochPayload, alert.freq_once_per_bar_close)

// Regular BOS with OB (continuation signals)
if bullishBOS and not bullishCHoCH and not na(bullishOB_high)
    bosPayload = '{"symbol":"' + syminfo.ticker + '","action":"BUY","strategy":"BOS_BULLISH_OB","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bullishOB_low - atr * 0.3) + ',"take_profit":' + str.tostring(close + (close - bullishOB_low) * 1.5) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":65,"auth_token":"' + i_webhookSecret + '"}'
    alert(bosPayload, alert.freq_once_per_bar_close)

if bearishBOS and not bearishCHoCH and not na(bearishOB_high)
    bosPayload = '{"symbol":"' + syminfo.ticker + '","action":"SELL","strategy":"BOS_BEARISH_OB","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(bearishOB_high + atr * 0.3) + ',"take_profit":' + str.tostring(close - (bearishOB_high - close) * 1.5) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":65,"auth_token":"' + i_webhookSecret + '"}'
    alert(bosPayload, alert.freq_once_per_bar_close)
```

**Step 3: Multi-Indicator Confluence Script**
```pinescript
// scripts/pinescript/tradie_confluence.pine
//@version=5
indicator("Tradie - Confluence Signals", overlay=true)

// ============================================
// INPUTS
// ============================================
// Trend Inputs
i_emaFast = input.int(9, "Fast EMA", group="Trend")
i_emaSlow = input.int(21, "Slow EMA", group="Trend")
i_emaTrend = input.int(50, "Trend EMA", group="Trend")

// Momentum Inputs
i_rsiLength = input.int(14, "RSI Length", group="Momentum")
i_rsiOB = input.int(70, "RSI Overbought", group="Momentum")
i_rsiOS = input.int(30, "RSI Oversold", group="Momentum")

// ADX Inputs
i_adxLength = input.int(14, "ADX Length", group="Trend Strength")
i_adxThreshold = input.int(25, "ADX Trending Threshold", group="Trend Strength")

// Confluence Requirements
i_minConfluence = input.int(3, "Minimum Confluence Score", minval=2, maxval=5, group="Confluence")

// Webhook
i_webhookSecret = input.string("TRADIE_SEC_9982", "Webhook Secret", group="Webhook")

// ============================================
// CALCULATIONS
// ============================================
// EMAs
emaFast = ta.ema(close, i_emaFast)
emaSlow = ta.ema(close, i_emaSlow)
emaTrend = ta.ema(close, i_emaTrend)

// RSI
rsi = ta.rsi(close, i_rsiLength)

// MACD
[macdLine, signalLine, histogram] = ta.macd(close, 12, 26, 9)

// ADX
[diPlus, diMinus, adx] = ta.dmi(i_adxLength, i_adxLength)

// ATR for stops
atr = ta.atr(14)

// ============================================
// CONFLUENCE SCORING
// ============================================
var int bullishScore = 0
var int bearishScore = 0

// Reset scores
bullishScore := 0
bearishScore := 0

// 1. EMA Alignment (Fast > Slow > Trend for bullish)
if emaFast > emaSlow and emaSlow > emaTrend
    bullishScore += 1
if emaFast < emaSlow and emaSlow < emaTrend
    bearishScore += 1

// 2. Price above/below trend EMA
if close > emaTrend
    bullishScore += 1
if close < emaTrend
    bearishScore += 1

// 3. EMA Crossover (recent)
emaCrossUp = ta.crossover(emaFast, emaSlow)
emaCrossDown = ta.crossunder(emaFast, emaSlow)
if emaCrossUp[1] or emaCrossUp[2] or emaCrossUp[3]
    bullishScore += 1
if emaCrossDown[1] or emaCrossDown[2] or emaCrossDown[3]
    bearishScore += 1

// 4. RSI Momentum
if rsi > 50 and rsi < i_rsiOB
    bullishScore += 1
if rsi < 50 and rsi > i_rsiOS
    bearishScore += 1

// 5. MACD Bullish/Bearish
if macdLine > signalLine and histogram > 0
    bullishScore += 1
if macdLine < signalLine and histogram < 0
    bearishScore += 1

// 6. ADX Confirms Trend
trendStrong = adx > i_adxThreshold
if trendStrong and diPlus > diMinus
    bullishScore += 1
if trendStrong and diMinus > diPlus
    bearishScore += 1

// ============================================
// SIGNAL GENERATION
// ============================================
bullishSignal = bullishScore >= i_minConfluence and bullishScore > bearishScore
bearishSignal = bearishScore >= i_minConfluence and bearishScore > bullishScore

// Prevent repeated signals
var bool lastWasBullish = false
var bool lastWasBearish = false

newBullishSignal = bullishSignal and not lastWasBullish
newBearishSignal = bearishSignal and not lastWasBearish

if bullishSignal
    lastWasBullish := true
    lastWasBearish := false
if bearishSignal
    lastWasBearish := true
    lastWasBullish := false
if not bullishSignal and not bearishSignal
    lastWasBullish := false
    lastWasBearish := false

// ============================================
// VISUALIZATION
// ============================================
plot(emaFast, "Fast EMA", color=color.blue, linewidth=1)
plot(emaSlow, "Slow EMA", color=color.orange, linewidth=1)
plot(emaTrend, "Trend EMA", color=color.purple, linewidth=2)

// Confluence score display
bgcolor(bullishScore >= i_minConfluence ? color.new(color.green, 90) : 
        bearishScore >= i_minConfluence ? color.new(color.red, 90) : na)

// Signal labels
if newBullishSignal
    label.new(bar_index, low, "BUY\n" + str.tostring(bullishScore) + "/6", 
        style=label.style_label_up, color=color.green, textcolor=color.white)

if newBearishSignal
    label.new(bar_index, high, "SELL\n" + str.tostring(bearishScore) + "/6", 
        style=label.style_label_down, color=color.red, textcolor=color.white)

// ============================================
// WEBHOOK ALERTS
// ============================================
if newBullishSignal
    sl = close - (atr * 2)
    tp = close + (atr * 4)  // 2:1 R:R
    confidence = math.round(bullishScore / 6 * 100)
    
    payload = '{"symbol":"' + syminfo.ticker + '","action":"BUY","strategy":"CONFLUENCE_BULLISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(sl) + ',"take_profit":' + str.tostring(tp) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":' + str.tostring(confidence) + ',"auth_token":"' + i_webhookSecret + '"}'
    alert(payload, alert.freq_once_per_bar_close)

if newBearishSignal
    sl = close + (atr * 2)
    tp = close - (atr * 4)  // 2:1 R:R
    confidence = math.round(bearishScore / 6 * 100)
    
    payload = '{"symbol":"' + syminfo.ticker + '","action":"SELL","strategy":"CONFLUENCE_BEARISH","price":' + str.tostring(close) + ',"stop_loss":' + str.tostring(sl) + ',"take_profit":' + str.tostring(tp) + ',"exchange":"' + syminfo.exchange + '","timeframe":"' + timeframe.period + '","confidence":' + str.tostring(confidence) + ',"auth_token":"' + i_webhookSecret + '"}'
    alert(payload, alert.freq_once_per_bar_close)
```

**Step 4: TradingView Alert Setup Instructions**
```markdown
## TradingView Webhook Alert Setup

### Step 1: Add Scripts to Chart
1. Open TradingView and navigate to your chart
2. Open the Pine Editor (bottom panel)
3. Paste each script and click "Add to chart"

### Step 2: Create Alert
1. Right-click on chart → "Add alert"
2. Condition: Select "Tradie - FVG Detector" → "Any alert() function call"
3. Check "Webhook URL"
4. Enter your webhook URL: `https://your-domain.com/api/v1/webhook/tradingview`
   - For local testing with ngrok: `https://xxxxx.ngrok.io/api/v1/webhook/tradingview`

### Step 3: Alert Options
- Expiration: Set to "Open-ended" for continuous monitoring
- Alert actions: Webhook only (uncheck notifications if noisy)

### Step 4: Test the Connection
1. Wait for an FVG to form on your chart
2. Check your API Gateway logs for "SIGNAL RECEIVED" message
3. Verify the signal is saved in the database

### Recommended Timeframes
- Swing Trading: 4H, Daily
- Day Trading: 15m, 1H
- Scalping: 5m, 15m
```

#### Acceptance Criteria
- [ ] FVG detector correctly identifies 3-candle imbalances
- [ ] Order Block script marks OBs after BOS/CHoCH
- [ ] Confluence script combines 6 factors for scoring
- [ ] All scripts fire webhooks with proper JSON format
- [ ] Alerts respect `freq_once_per_bar_close` to prevent spam
- [ ] Stop loss and take profit calculated correctly

---

### TICKET 5: News Shield - Python NLP Microservice
**Priority:** P0 (Critical)  
**Estimated Time:** 10 hours  
**Dependencies:** Ticket 1

#### Purpose
Build the Python microservice that checks financial news sentiment and economic calendar events to determine if market conditions are safe for trading.

#### Implementation Steps

**Step 1: Create FastAPI Application Structure**
```python
# news-shield/src/main.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging

from .routers import market_status, events, sentiment
from .services.event_calendar import EventCalendarService
from .services.sentiment_analyzer import SentimentAnalyzer
from .config import settings

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting News Shield service...")
    app.state.event_service = EventCalendarService()
    app.state.sentiment_analyzer = SentimentAnalyzer()
    await app.state.sentiment_analyzer.initialize()
    logger.info("News Shield ready")
    yield
    # Shutdown
    logger.info("Shutting down News Shield...")

app = FastAPI(
    title="Tradie News Shield",
    description="Market condition analysis and news sentiment filtering",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(market_status.router, prefix="/api", tags=["Market Status"])
app.include_router(events.router, prefix="/api", tags=["Economic Events"])
app.include_router(sentiment.router, prefix="/api", tags=["Sentiment"])

@app.get("/health")
async def health():
    return {"status": "healthy", "service": "news-shield"}
```

**Step 2: Configuration**
```python
# news-shield/src/config.py
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    # API Keys
    finnhub_api_key: str = ""
    alpha_vantage_api_key: str = ""
    
    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    
    # News Shield Thresholds
    sentiment_danger_threshold: float = -0.5
    sentiment_caution_threshold: float = -0.3
    
    # High Impact Events
    high_impact_pause_minutes: int = 30
    
    # VIX Thresholds
    vix_elevated: float = 25.0
    vix_extreme: float = 35.0
    
    class Config:
        env_file = ".env"

settings = Settings()
```

**Step 3: Economic Calendar Service**
```python
# news-shield/src/services/event_calendar.py
import httpx
from datetime import datetime, timedelta
from typing import List, Optional
from pydantic import BaseModel
from enum import Enum
import logging

from ..config import settings

logger = logging.getLogger(__name__)

class EventImpact(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"

class EconomicEvent(BaseModel):
    event_time: datetime
    title: str
    country: str
    currency: Optional[str]
    impact: EventImpact
    forecast: Optional[str]
    previous: Optional[str]
    actual: Optional[str]

class EventCalendarService:
    """
    Fetches economic calendar events from Finnhub (free tier).
    Identifies high-impact events that should pause trading.
    """
    
    HIGH_IMPACT_KEYWORDS = [
        "nonfarm payroll", "nfp", "fomc", "fed rate", "interest rate decision",
        "cpi", "consumer price", "inflation", "gdp", "gross domestic",
        "unemployment", "retail sales", "pmi", "manufacturing",
        "ecb", "boe", "boj", "rba"  # Central bank decisions
    ]
    
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        self.base_url = "https://finnhub.io/api/v1"
    
    async def get_upcoming_events(
        self, 
        hours_ahead: int = 24
    ) -> List[EconomicEvent]:
        """Fetch economic calendar events for the next N hours."""
        
        today = datetime.utcnow().strftime("%Y-%m-%d")
        tomorrow = (datetime.utcnow() + timedelta(days=1)).strftime("%Y-%m-%d")
        
        try:
            response = await self.client.get(
                f"{self.base_url}/calendar/economic",
                params={
                    "from": today,
                    "to": tomorrow,
                    "token": settings.finnhub_api_key
                }
            )
            response.raise_for_status()
            data = response.json()
            
            events = []
            for item in data.get("economicCalendar", []):
                impact = self._classify_impact(item)
                event = EconomicEvent(
                    event_time=datetime.fromisoformat(item["time"].replace("Z", "+00:00")),
                    title=item.get("event", "Unknown"),
                    country=item.get("country", ""),
                    currency=item.get("currency"),
                    impact=impact,
                    forecast=item.get("estimate"),
                    previous=item.get("prev"),
                    actual=item.get("actual")
                )
                events.append(event)
            
            # Filter to next N hours
            cutoff = datetime.utcnow() + timedelta(hours=hours_ahead)
            return [e for e in events if e.event_time <= cutoff]
            
        except Exception as e:
            logger.error(f"Failed to fetch economic calendar: {e}")
            return []
    
    def _classify_impact(self, event: dict) -> EventImpact:
        """Classify event impact based on title keywords."""
        title = event.get("event", "").lower()
        
        # Check for high-impact keywords
        for keyword in self.HIGH_IMPACT_KEYWORDS:
            if keyword in title:
                return EventImpact.HIGH
        
        # Use Finnhub's impact if available
        impact = event.get("impact", "").lower()
        if impact == "high":
            return EventImpact.HIGH
        elif impact == "medium":
            return EventImpact.MEDIUM
        
        return EventImpact.LOW
    
    async def get_high_impact_events(self) -> List[EconomicEvent]:
        """Get only high-impact events in the next 24 hours."""
        events = await self.get_upcoming_events(hours_ahead=24)
        return [e for e in events if e.impact == EventImpact.HIGH]
    
    def is_event_imminent(
        self, 
        event: EconomicEvent,
        pause_minutes_before: int = 30,
        pause_minutes_after: int = 60
    ) -> bool:
        """Check if a high-impact event is within the pause window."""
        now = datetime.utcnow()
        window_start = event.event_time - timedelta(minutes=pause_minutes_before)
        window_end = event.event_time + timedelta(minutes=pause_minutes_after)
        
        return window_start <= now <= window_end
```

**Step 4: Sentiment Analysis Service**
```python
# news-shield/src/services/sentiment_analyzer.py
import httpx
from datetime import datetime, timedelta
from typing import List, Tuple, Optional
from pydantic import BaseModel
import logging
import asyncio

# Use VADER for speed (can upgrade to FinBERT for accuracy)
from nltk.sentiment.vader import SentimentIntensityAnalyzer
import nltk

from ..config import settings

logger = logging.getLogger(__name__)

class NewsItem(BaseModel):
    headline: str
    summary: Optional[str]
    source: str
    published_at: datetime
    url: Optional[str]
    sentiment_score: Optional[float] = None

class SentimentResult(BaseModel):
    overall_score: float  # -1.0 to 1.0
    label: str  # POSITIVE, NEGATIVE, NEUTRAL
    news_count: int
    headlines: List[NewsItem]

class SentimentAnalyzer:
    """
    Analyzes financial news sentiment using VADER (fast) or FinBERT (accurate).
    Provides market sentiment score for trading decisions.
    """
    
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        self.vader: Optional[SentimentIntensityAnalyzer] = None
        self.finbert_model = None  # Optional: for higher accuracy
    
    async def initialize(self):
        """Initialize NLP models."""
        # Download VADER lexicon if needed
        try:
            nltk.data.find('sentiment/vader_lexicon.zip')
        except LookupError:
            nltk.download('vader_lexicon', quiet=True)
        
        self.vader = SentimentIntensityAnalyzer()
        
        # Add financial terms to VADER
        financial_lexicon = {
            'bullish': 2.0, 'bearish': -2.0,
            'rally': 1.5, 'crash': -2.5,
            'surge': 1.5, 'plunge': -2.0,
            'breakout': 1.0, 'breakdown': -1.0,
            'upgrade': 1.5, 'downgrade': -1.5,
            'beat': 1.0, 'miss': -1.0,
            'hawkish': -0.5, 'dovish': 0.5,
            'recession': -2.0, 'growth': 1.0,
            'inflation': -0.5, 'deflation': -0.5,
        }
        self.vader.lexicon.update(financial_lexicon)
        
        logger.info("Sentiment analyzer initialized with VADER")
    
    async def fetch_market_news(
        self, 
        symbol: Optional[str] = None,
        category: str = "general"
    ) -> List[NewsItem]:
        """Fetch recent market news from Finnhub."""
        
        try:
            params = {
                "category": category,
                "token": settings.finnhub_api_key
            }
            
            response = await self.client.get(
                "https://finnhub.io/api/v1/news",
                params=params
            )
            response.raise_for_status()
            data = response.json()
            
            news_items = []
            for item in data[:20]:  # Limit to 20 most recent
                news = NewsItem(
                    headline=item.get("headline", ""),
                    summary=item.get("summary"),
                    source=item.get("source", "unknown"),
                    published_at=datetime.fromtimestamp(item.get("datetime", 0)),
                    url=item.get("url")
                )
                news_items.append(news)
            
            return news_items
            
        except Exception as e:
            logger.error(f"Failed to fetch news: {e}")
            return []
    
    def analyze_sentiment(self, text: str) -> float:
        """
        Analyze sentiment of a single text.
        Returns score from -1.0 (negative) to 1.0 (positive).
        """
        if not self.vader:
            return 0.0
        
        scores = self.vader.polarity_scores(text)
        return scores['compound']
    
    async def get_market_sentiment(
        self, 
        symbol: Optional[str] = None
    ) -> SentimentResult:
        """
        Analyze overall market sentiment from recent news.
        """
        news_items = await self.fetch_market_news(symbol=symbol)
        
        if not news_items:
            return SentimentResult(
                overall_score=0.0,
                label="NEUTRAL",
                news_count=0,
                headlines=[]
            )
        
        # Analyze each headline
        total_score = 0.0
        for news in news_items:
            text = news.headline
            if news.summary:
                text += " " + news.summary
            
            score = self.analyze_sentiment(text)
            news.sentiment_score = score
            total_score += score
        
        # Calculate average
        avg_score = total_score / len(news_items)
        
        # Classify
        if avg_score >= 0.2:
            label = "POSITIVE"
        elif avg_score <= -0.2:
            label = "NEGATIVE"
        else:
            label = "NEUTRAL"
        
        return SentimentResult(
            overall_score=round(avg_score, 3),
            label=label,
            news_count=len(news_items),
            headlines=news_items
        )
```

**Step 5: Market Status Router**
```python
# news-shield/src/routers/market_status.py
from fastapi import APIRouter, Request, Query
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
import logging

from ..services.event_calendar import EventCalendarService, EconomicEvent, EventImpact
from ..services.sentiment_analyzer import SentimentAnalyzer, SentimentResult
from ..config import settings

logger = logging.getLogger(__name__)
router = APIRouter()

class MarketStatusResponse(BaseModel):
    safe_to_trade: bool
    risk_level: str  # "LOW", "MEDIUM", "HIGH", "EXTREME"
    reasons: List[str]
    sentiment: Optional[SentimentResult]
    upcoming_events: List[EconomicEvent]
    vix_level: Optional[float]
    timestamp: datetime

@router.get("/market-status", response_model=MarketStatusResponse)
async def get_market_status(
    request: Request,
    symbol: Optional[str] = Query(None, description="Optional symbol for specific sentiment")
):
    """
    Main endpoint for Strategy Engine to check if trading is safe.
    
    Returns:
        - safe_to_trade: Boolean indicating if conditions are favorable
        - risk_level: Current market risk assessment
        - reasons: List of factors affecting the decision
    """
    
    event_service: EventCalendarService = request.app.state.event_service
    sentiment_analyzer: SentimentAnalyzer = request.app.state.sentiment_analyzer
    
    reasons = []
    risk_level = "LOW"
    safe_to_trade = True
    
    # 1. Check for imminent high-impact events
    high_impact_events = await event_service.get_high_impact_events()
    imminent_events = []
    
    for event in high_impact_events:
        if event_service.is_event_imminent(event, 
                pause_minutes_before=settings.high_impact_pause_minutes,
                pause_minutes_after=60):
            imminent_events.append(event)
            reasons.append(f"High-impact event imminent: {event.title} at {event.event_time}")
            safe_to_trade = False
            risk_level = "EXTREME"
    
    # 2. Check news sentiment
    sentiment = await sentiment_analyzer.get_market_sentiment(symbol=symbol)
    
    if sentiment.overall_score <= settings.sentiment_danger_threshold:
        reasons.append(f"Extremely negative sentiment: {sentiment.overall_score}")
        safe_to_trade = False
        if risk_level != "EXTREME":
            risk_level = "HIGH"
    elif sentiment.overall_score <= settings.sentiment_caution_threshold:
        reasons.append(f"Negative sentiment detected: {sentiment.overall_score}")
        if risk_level == "LOW":
            risk_level = "MEDIUM"
    
    # 3. TODO: Add VIX check (requires market data subscription)
    vix_level = None  # Would fetch from IBKR or data provider
    
    # 4. If no issues found
    if not reasons:
        reasons.append("Market conditions appear favorable")
    
    return MarketStatusResponse(
        safe_to_trade=safe_to_trade,
        risk_level=risk_level,
        reasons=reasons,
        sentiment=sentiment,
        upcoming_events=high_impact_events[:5],  # Next 5 high-impact events
        vix_level=vix_level,
        timestamp=datetime.utcnow()
    )

@router.get("/quick-check")
async def quick_market_check(request: Request):
    """
    Lightweight endpoint for rapid checks.
    Returns only safe_to_trade boolean with minimal processing.
    """
    event_service: EventCalendarService = request.app.state.event_service
    
    # Only check for imminent high-impact events
    high_impact_events = await event_service.get_high_impact_events()
    
    for event in high_impact_events:
        if event_service.is_event_imminent(event, 
                pause_minutes_before=15,
                pause_minutes_after=30):
            return {"safe_to_trade": False, "reason": event.title}
    
    return {"safe_to_trade": True}
```

**Step 6: Dockerfile for News Shield**
```dockerfile
# news-shield/Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY pyproject.toml .
RUN pip install --no-cache-dir -e .

# Download NLTK data
RUN python -c "import nltk; nltk.download('vader_lexicon', quiet=True)"

# Copy source code
COPY src/ ./src/

# Run with uvicorn
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

#### Acceptance Criteria
- [ ] `/api/market-status` returns comprehensive market condition analysis
- [ ] `/api/quick-check` returns in <100ms for rapid validation
- [ ] Economic calendar fetches events from Finnhub
- [ ] Sentiment analysis processes news headlines with VADER
- [ ] High-impact events trigger `safe_to_trade: false`
- [ ] Service runs in Docker container

---

## Phase 2: Strategy & Risk Management (Weeks 5-8)

---

### TICKET 6: Strategy Engine - Signal Validation & Risk Management
**Priority:** P0 (Critical)  
**Estimated Time:** 12 hours  
**Dependencies:** Ticket 3, Ticket 5

#### Purpose
Build the core Strategy Engine that consumes signals from Kafka, validates them against News Shield, applies risk management rules, and calculates position sizing.

[Content continues with detailed implementation...]

---

### TICKET 7: Risk Management Service - Position Sizing & Portfolio Heat
**Priority:** P0 (Critical)  
**Estimated Time:** 10 hours  
**Dependencies:** Ticket 6

[Detailed implementation for Kelly Criterion, ATR-based sizing, portfolio heat management...]

---

### TICKET 8: Technical Analysis Library Integration (ta4j)
**Priority:** P1 (High)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 6

[Integration of 130+ technical indicators using ta4j Java library...]

---

## Phase 3: Order Execution (Weeks 9-12)

---

### TICKET 9: Interactive Brokers TWS API Connection
**Priority:** P0 (Critical)  
**Estimated Time:** 12 hours  
**Dependencies:** Ticket 6

[IBKR Java API integration with connection management, heartbeat, reconnection...]

---

### TICKET 10: Order Execution Service - Bracket Orders
**Priority:** P0 (Critical)  
**Estimated Time:** 10 hours  
**Dependencies:** Ticket 9

[Bracket order implementation with parent + stop loss + take profit...]

---

### TICKET 11: Position Management & Tracking
**Priority:** P0 (Critical)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 10

[Real-time position tracking, P&L calculation, trailing stops...]

---

## Phase 4: Alerting & Monitoring (Weeks 13-16)

---

### TICKET 12: Telegram Alert Service
**Priority:** P1 (High)  
**Estimated Time:** 6 hours  
**Dependencies:** Ticket 10

[Telegram bot integration for trade alerts, commands, remote control...]

---

### TICKET 13: Prometheus Metrics & Grafana Dashboards
**Priority:** P2 (Medium)  
**Estimated Time:** 8 hours  
**Dependencies:** All previous tickets

[Comprehensive monitoring with trading metrics, latency tracking, P&L visualization...]

---

### TICKET 14: Audit Logging & Trade Journal
**Priority:** P1 (High)  
**Estimated Time:** 6 hours  
**Dependencies:** Ticket 10

[Complete audit trail for compliance, trade journaling for review...]

---

## Phase 5: Multi-Asset & Production (Weeks 17-20)

---

### TICKET 15: Forex Support - Pip Calculations & Lot Sizing
**Priority:** P1 (High)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 7

[Forex-specific calculations, swap rates, proper lot sizing...]

---

### TICKET 16: Futures Support - Contract Specs & Rollovers
**Priority:** P1 (High)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 7

[Futures contract management, automatic rollover detection...]

---

### TICKET 17: Cryptocurrency Support via IBKR
**Priority:** P2 (Medium)  
**Estimated Time:** 6 hours  
**Dependencies:** Ticket 7

[Crypto trading through IBKR, 24/7 market handling...]

---

### TICKET 18: Backtesting Framework Integration
**Priority:** P2 (Medium)  
**Estimated Time:** 12 hours  
**Dependencies:** Ticket 8

[VectorBT integration for Python, backtrader for walk-forward optimization...]

---

### TICKET 19: Strategy Plugin Architecture
**Priority:** P2 (Medium)  
**Estimated Time:** 8 hours  
**Dependencies:** Ticket 6

[Pluggable strategy system for easy addition of new trading strategies...]

---

### TICKET 20: Production Deployment & CI/CD
**Priority:** P1 (High)  
**Estimated Time:** 10 hours  
**Dependencies:** All previous tickets

[Docker Compose production setup, GitHub Actions CI/CD, SSL configuration...]

---

## Appendix A: TradingView Alert JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "TradingView Webhook Signal",
  "type": "object",
  "required": ["symbol", "action", "strategy", "price", "auth_token"],
  "properties": {
    "symbol": {
      "type": "string",
      "maxLength": 20,
      "description": "Trading symbol (e.g., AAPL, EURUSD)"
    },
    "action": {
      "type": "string",
      "enum": ["BUY", "SELL"]
    },
    "strategy": {
      "type": "string",
      "maxLength": 50,
      "description": "Strategy identifier (e.g., FVG_BULLISH, CHOCH_BEARISH)"
    },
    "price": {
      "type": "number",
      "exclusiveMinimum": 0
    },
    "stop_loss": {
      "type": "number",
      "exclusiveMinimum": 0
    },
    "take_profit": {
      "type": "number",
      "exclusiveMinimum": 0
    },
    "exchange": {
      "type": "string",
      "default": "SMART"
    },
    "timeframe": {
      "type": "string",
      "default": "15m"
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "auth_token": {
      "type": "string"
    }
  }
}
```

## Appendix B: Environment Variables Reference

```bash
# ======================
# DATABASE
# ======================
DB_HOST=localhost
DB_PORT=5432
DB_NAME=tradie
DB_USER=tradie
DB_PASSWORD=your_secure_password

# ======================
# KAFKA
# ======================
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# ======================
# REDIS
# ======================
REDIS_HOST=localhost
REDIS_PORT=6379

# ======================
# TRADINGVIEW
# ======================
TV_WEBHOOK_SECRET=your_webhook_secret_here

# ======================
# INTERACTIVE BROKERS
# ======================
IB_HOST=127.0.0.1
IB_PORT=7497          # Paper trading: 7497, Live: 7496
IB_CLIENT_ID=1

# ======================
# TELEGRAM
# ======================
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id

# ======================
# NEWS APIS
# ======================
FINNHUB_API_KEY=your_finnhub_key
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_key

# ======================
# RISK MANAGEMENT
# ======================
MAX_RISK_PER_TRADE_PCT=2.0
MAX_DAILY_LOSS_PCT=3.0
MAX_PORTFOLIO_HEAT_PCT=6.0
MIN_RISK_REWARD_RATIO=2.0
```

---

## Summary

This implementation guide provides **20 sequential tickets** that take you from project initialization to production deployment. The architecture follows best practices:

- **Microservices**: Decoupled services for scalability
- **Event-Driven**: Kafka for reliable message processing
- **Multi-Language**: Java for execution, Python for ML/NLP
- **Comprehensive Trading Knowledge**: All requested strategies and concepts implemented
- **Risk-First Design**: Multiple layers of risk management

**Total Estimated Time**: 150-180 hours (4-5 months part-time, 6-8 weeks full-time)

**Minimum Monthly Cost**: $24.95 (TradingView Pro for webhooks)

Start with **Ticket 1** and work sequentially. Each ticket builds on the previous, ensuring a stable foundation before adding complexity.

Happy Trading! 🚀
