# Tradie Bot - System Overview

## Project Summary

**Tradie** is an autonomous, multi-strategy trading bot designed for swing trading and active/day trading across multiple asset classes including stocks, cryptocurrency, forex, and futures.

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Primary Focus** | Swing Trading & Day Trading |
| **Asset Classes** | Stocks, Crypto, Forex, Futures |
| **Broker** | Interactive Brokers (USD) |
| **Charts** | TradingView (Webhook Integration) |
| **Cash Flow** | Grey.co |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              TRADIE SYSTEM                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  LAYER 1: SIGNAL GENERATION (TradingView - "The Eyes")                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Pine Scripts:                                                       │    │
│  │  • FVG Detector (Fair Value Gaps)                                   │    │
│  │  • Order Block & BOS/CHoCH Scanner                                  │    │
│  │  • Multi-Indicator Confluence (EMA, RSI, MACD, ADX)                │    │
│  │                                     │                                │    │
│  │                                     ▼ Webhook (JSON)                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                        │                                     │
│  LAYER 2: INGESTION & ROUTING (Spring Boot API Gateway)                     │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  • Webhook Authentication (Secret Token)                            │    │
│  │  • Signal Validation & Persistence                                  │    │
│  │  • Kafka Message Publishing                                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                        │                                     │
│                             Kafka Topics: signals, orders, alerts            │
│                                        │                                     │
│  LAYER 3: INTELLIGENCE (Python + Java Microservices)                        │
│  ┌───────────────────────┐    ┌───────────────────────┐                    │
│  │    NEWS SHIELD        │    │   STRATEGY ENGINE     │                    │
│  │    (Python/FastAPI)   │    │   (Java/Spring Boot)  │                    │
│  │                       │    │                       │                    │
│  │  • Finnhub News API   │───▶│  • Signal Validation  │                    │
│  │  • VADER Sentiment    │    │  • Kill Zone Check    │                    │
│  │  • Economic Calendar  │    │  • Risk Rules         │                    │
│  │  • VIX Monitoring     │    │  • Position Sizing    │                    │
│  │                       │    │  • Kelly Criterion    │                    │
│  │  Output:              │    │                       │                    │
│  │  safe_to_trade: bool  │    │  Output: OrderDTO     │                    │
│  └───────────────────────┘    └───────────────────────┘                    │
│                                        │                                     │
│  LAYER 4: EXECUTION (Interactive Brokers TWS API)                           │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  • Bracket Order Construction (Entry + SL + TP)                     │    │
│  │  • Order Submission via Java Client                                 │    │
│  │  • Position Tracking & P&L Calculation                             │    │
│  │  • Trailing Stop Management                                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                        │                                     │
│  LAYER 5: PERSISTENCE & ALERTS                                              │
│  ┌───────────────────────┐    ┌───────────────────────┐                    │
│  │    TimescaleDB        │    │   Telegram Bot        │                    │
│  │  • OHLCV Data         │    │  • Trade Alerts       │                    │
│  │  • Trade Signals      │    │  • Error Notifications│                    │
│  │  • Order History      │    │  • /pause Command     │                    │
│  │  • Audit Logs         │    │  • Daily P&L Summary  │                    │
│  └───────────────────────┘    └───────────────────────┘                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### 1. Signal Generation Flow
```
TradingView Chart → Pine Script Detects Pattern → Webhook Alert Fired
                    (FVG, Order Block, Confluence)    (JSON Payload)
```

### 2. Signal Processing Flow
```
Webhook Received → Token Validated → Signal Persisted → Published to Kafka
                   (API Gateway)     (PostgreSQL)       (signals topic)
```

### 3. Validation Flow
```
Strategy Engine Consumes Signal → Calls News Shield → Checks Kill Zones
         │                              │                    │
         │                              ▼                    │
         │                    safe_to_trade: true/false      │
         │                              │                    │
         ▼                              ▼                    ▼
    Applies Risk Rules ←────── If Safe ──────→ Calculate Position Size
         │
         ▼
    Publishes OrderDTO to Kafka (orders topic)
```

### 4. Execution Flow
```
Order Executor Consumes OrderDTO → Constructs IBKR Bracket Order
         │
         ├── Parent Order: LIMIT entry at signal price
         ├── Stop Loss: STOP order
         └── Take Profit: LIMIT order
         │
         ▼
    Submits to IBKR TWS API → Trade Executed → Position Tracked
```

### 5. Notification Flow
```
Trade Executed → Event Published to Kafka (alerts topic)
         │
         ▼
Alert Service Consumes → Formats Message → Sends Telegram Notification
```

---

## Technology Stack

### Core Backend (Java 21 + Spring Boot 3.x)
| Component | Technology | Purpose |
|-----------|------------|---------|
| API Gateway | Spring Boot | Webhook ingestion |
| Strategy Engine | Spring Boot | Signal validation |
| Order Executor | Spring Boot + IBKR API | Trade execution |
| Technical Analysis | ta4j | 130+ indicators |

### Analytics Service (Python 3.11+)
| Component | Technology | Purpose |
|-----------|------------|---------|
| News Shield | FastAPI | Market condition analysis |
| Sentiment | VADER / FinBERT | News sentiment scoring |
| Backtesting | VectorBT | Strategy optimization |

### Infrastructure
| Component | Technology | Purpose |
|-----------|------------|---------|
| Time-Series DB | TimescaleDB | OHLCV & trade data |
| Message Broker | Apache Kafka | Event streaming |
| Cache | Redis | Real-time state |
| Monitoring | Prometheus + Grafana | Metrics & dashboards |

---

## Trading Knowledge Modules

### Module A: Smart Money Concepts (ICT/SMC)
- Fair Value Gaps (FVG)
- Order Blocks & Breaker Blocks
- Liquidity Sweeps (Stop Hunts)
- Kill Zones (London, NY, Silver Bullet)
- Optimal Trade Entry (OTE)
- Market Structure Shift (MSS)
- Break of Structure (BOS) / Change of Character (CHoCH)

### Module B: Technical Indicators
- **Momentum**: RSI, MACD, Stochastic, CCI
- **Trend**: SMA, EMA, ADX, Ichimoku
- **Volatility**: Bollinger Bands, ATR, Keltner
- **Volume**: OBV, VWAP, Volume Profile

### Module C: Trading Strategies
- Trend Following
- Breakout Trading
- Pullback Trading
- Range Trading
- Gap Trading
- Momentum Trading
- Fibonacci Retracement

### Module D: Risk Management
- Fixed Fractional Position Sizing (1-2% per trade)
- Kelly Criterion (Quarter Kelly)
- ATR-Based Stop Loss
- 2:1 Minimum Risk/Reward
- Portfolio Heat Monitoring (6% max)
- Daily Loss Limits (3% max)

---

## Implementation Phases

| Phase | Weeks | Focus | Tickets |
|-------|-------|-------|---------|
| **1. Foundation** | 1-4 | Project setup, database, API Gateway | 1-5 |
| **2. Strategy** | 5-8 | Strategy Engine, risk management | 6-8 |
| **3. Execution** | 9-12 | IBKR integration, order management | 9-11 |
| **4. Alerting** | 13-16 | Telegram, monitoring, logging | 12-14 |
| **5. Production** | 17-20 | Multi-asset, backtesting, deployment | 15-20 |

---

## Cost Summary

### Minimum Viable Setup: $24.95/month
| Item | Cost |
|------|------|
| TradingView Pro (webhooks) | $24.95/mo |
| Oracle Cloud VM (free tier) | $0 |
| Self-hosted TimescaleDB | $0 |
| Finnhub API (free tier) | $0 |
| Telegram Bot | $0 |
| **Total** | **$24.95/mo** |

### Trading Costs (Per Trade)
| Asset | Commission |
|-------|------------|
| Stocks | $0.005/share (min $1) |
| Options | $0.65/contract |
| Forex | No commission (spread) |
| Futures | $0.85/contract |

---

## Quick Start

1. **Clone Repository**
   ```bash
   git clone https://github.com/yourusername/tradie-bot.git
   cd tradie-bot
   ```

2. **Start Infrastructure**
   ```bash
   docker-compose up -d
   ```

3. **Configure Environment**
   ```bash
   cp .env.example .env
   # Edit .env with your API keys
   ```

4. **Run Services**
   ```bash
   ./gradlew bootRun  # Java services
   uvicorn src.main:app --port 8001  # News Shield
   ```

5. **Add TradingView Scripts**
   - Add Pine Scripts to your TradingView chart
   - Configure webhook URL: `https://your-domain/api/v1/webhook/tradingview`

6. **Start Paper Trading**
   - Connect IBKR TWS with paper trading port (7497)
   - Monitor via Telegram bot

---

## Key Files

```
tradie-bot/
├── api-gateway/               # Webhook receiver
│   └── src/main/java/
│       └── controller/WebhookController.java
├── strategy-engine/           # Signal validation
│   └── src/main/java/
│       └── service/StrategyService.java
├── order-executor/            # IBKR integration
│   └── src/main/java/
│       └── service/IBKROrderService.java
├── news-shield/               # Python NLP service
│   └── src/
│       └── services/sentiment_analyzer.py
├── scripts/pinescript/        # TradingView scripts
│   ├── tradie_fvg_detector.pine
│   └── tradie_order_blocks.pine
├── infrastructure/
│   └── docker-compose.yml
└── docs/
    └── Overview.md
```

---

## Support & Resources

- **Interactive Brokers API**: https://interactivebrokers.github.io/tws-api/
- **TradingView Pine Script**: https://www.tradingview.com/pine-script-docs/
- **TimescaleDB Docs**: https://docs.timescale.com/
- **Finnhub API**: https://finnhub.io/docs/api

---

**Author**: Isaac Njoroge  
**Last Updated**: January 2026  
**License**: MIT
