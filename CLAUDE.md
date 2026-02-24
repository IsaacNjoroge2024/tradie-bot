# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Tradie Bot** is an autonomous, multi-strategy trading bot designed for swing trading and day trading across multiple asset classes (stocks, crypto, forex, futures). It integrates with TradingView for signal generation and Interactive Brokers for trade execution.

**Version**: 1.0.0
**Broker**: Interactive Brokers (TWS API)
**Charts**: TradingView (Webhook Integration)
**Supported Markets**: Stocks, Cryptocurrency, Forex, Futures

## System Architecture

The system follows a microservices architecture with 5 layers:

1. **Layer 1: Signal Generation** - TradingView Pine Scripts detect patterns (FVG, Order Blocks, BOS/CHoCH) and fire webhook alerts
2. **Layer 2: Ingestion & Routing** - Spring Boot API Gateway validates webhooks and publishes to Kafka
3. **Layer 3: Intelligence** - News Shield (Python/FastAPI) analyzes market conditions; Strategy Engine (Java) validates signals and applies risk rules
4. **Layer 4: Execution** - Order Executor constructs and submits bracket orders to Interactive Brokers
5. **Layer 5: Persistence & Alerts** - TimescaleDB stores trade data; Telegram Bot sends notifications

### Data Flow
```
TradingView → API Gateway → Kafka → Strategy Engine → Order Executor → IBKR
                               ↓            ↓               ↓
                          News Shield   Risk Mgmt    TimescaleDB
                                                           ↓
                                                    Telegram Alerts
```

## Technology Stack

### Core Backend (Java 21 + Spring Boot 3.x)
- **API Gateway**: Webhook receiver and authentication
- **Strategy Engine**: Signal validation and risk management
- **Order Executor**: IBKR TWS API integration
- **Technical Analysis**: ta4j library (130+ indicators)
- **Build Tool**: Gradle

### Analytics Service (Python 3.11+)
- **News Shield**: FastAPI-based NLP service for market sentiment analysis
- **Sentiment**: VADER / FinBERT for news scoring
- **Backtesting**: VectorBT for strategy optimization
- **Package Manager**: pip with pyproject.toml

### Infrastructure
- **Time-Series Database**: TimescaleDB (PostgreSQL-based)
- **Message Broker**: Apache Kafka
- **Cache**: Redis
- **Monitoring**: Prometheus + Grafana
- **Containerization**: Docker + Docker Compose

### External Integrations
- **Broker**: Interactive Brokers TWS API
- **Charts**: TradingView Webhooks
- **Alerts**: Telegram Bot API
- **News**: Finnhub / Alpha Vantage APIs
- **Economic Calendar**: TradingEconomics API

## Development Commands

### Initial Setup
```bash
# Clone and initialize
git clone <repo-url>
cd tradie-bot

# Start infrastructure services (TimescaleDB, Kafka, Redis)
docker-compose -f infrastructure/docker-compose.yml up -d

# Copy environment template
cp .env.example .env
# Edit .env with your API keys
```

### Java Services (Gradle)
```bash
# Build all Java services
./gradlew build

# Run specific service
./gradlew :api-gateway:bootRun
./gradlew :strategy-engine:bootRun
./gradlew :order-executor:bootRun

# Run tests
./gradlew test

# Run tests for specific service
./gradlew :api-gateway:test
```

### Python Service (News Shield)
```bash
# Setup virtual environment
cd news-shield
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install base + dev dependencies (no heavy ML packages - suitable for CI)
pip install -e ".[dev]"

# Install everything including ML packages (torch/transformers - large download)
pip install -e ".[ml,dev]"

# Run service
uvicorn src.main:app --port 8001 --reload

# Run tests
pytest

# Format code
black src/
```

### Infrastructure Management
```bash
# Start all services (run from repo root)
docker-compose -f infrastructure/docker-compose.yml up -d

# Stop all services
docker-compose -f infrastructure/docker-compose.yml down

# View logs
docker-compose -f infrastructure/docker-compose.yml logs -f [service-name]

# Reset database (removes all data volumes)
docker-compose -f infrastructure/docker-compose.yml down -v
docker-compose -f infrastructure/docker-compose.yml up -d timescaledb
```

### Gradle Wrapper (First-Time Setup)
```bash
# The gradle-wrapper.jar binary must be generated once after cloning.
# Requires Gradle installed locally (https://gradle.org/install/).
gradle wrapper --gradle-version 8.5
```

## Project Structure

```
tradie-bot/
├── api-gateway/              # Spring Boot - Webhook receiver
├── strategy-engine/          # Spring Boot - Signal validation & risk
├── order-executor/           # Spring Boot - IBKR integration
├── news-shield/             # Python/FastAPI - Sentiment analysis
├── alert-service/           # Telegram bot integration
├── common/                  # Shared Java libraries (DTOs, utils)
├── infrastructure/          # Docker compose, DB schemas
│   ├── docker-compose.yml
│   └── db/init.sql
├── scripts/
│   └── pinescript/         # TradingView Pine Scripts
│       ├── tradie_fvg_detector.pine
│       └── tradie_order_blocks.pine
├── data/
│   ├── historical/         # OHLCV data
│   ├── logs/              # Application logs
│   └── models/            # ML model artifacts
└── docs/
    ├── Overview.md
    └── tradie-implementation-guide.md
```

## Implementation Tickets

The project is organized into **20 sequential tickets** across 5 phases. All tickets are documented in `tradie-implementation-guide.md`.

### Phase 1: Foundation (Weeks 1-4)
- **Ticket 1**: Project initialization & repository setup
- **Ticket 2**: TimescaleDB schema & data models
- **Ticket 3**: API Gateway - webhook receiver & authentication
- **Ticket 4**: TradingView Pine Script - FVG & Order Block detection
- **Ticket 5**: News Shield - Python NLP microservice

### Phase 2: Strategy & Risk Management (Weeks 5-8)
- **Ticket 6**: Strategy Engine - signal validation
- **Ticket 7**: Risk Management Service - position sizing & portfolio heat
- **Ticket 8**: Technical Analysis Library integration (ta4j)

### Phase 3: Order Execution (Weeks 9-12)
- **Ticket 9**: Interactive Brokers TWS API connection
- **Ticket 10**: Order Execution Service - bracket orders
- **Ticket 11**: Position management & tracking

### Phase 4: Alerting & Monitoring (Weeks 13-16)
- **Ticket 12**: Telegram Alert Service
- **Ticket 13**: Prometheus metrics & Grafana dashboards
- **Ticket 14**: Audit logging & trade journal

### Phase 5: Multi-Asset & Production (Weeks 17-20)
- **Ticket 15**: Forex support - pip calculations & lot sizing
- **Ticket 16**: Futures support - contract specs & rollovers
- **Ticket 17**: Cryptocurrency support via IBKR
- **Ticket 18**: Backtesting framework integration
- **Ticket 19**: Strategy plugin architecture
- **Ticket 20**: Production deployment & CI/CD

**When working on tickets**: Read the specific ticket in `tradie-implementation-guide.md` for detailed implementation steps, acceptance criteria, and test cases.

## Environment Variables

Required environment variables (see `.env.example` or Appendix B in implementation guide):

### Core Configuration
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - TimescaleDB connection
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker address
- `REDIS_HOST`, `REDIS_PORT` - Redis cache connection

### External Services
- `TV_WEBHOOK_SECRET` - TradingView webhook authentication
- `IB_HOST`, `IB_PORT`, `IB_CLIENT_ID` - Interactive Brokers connection (7497 = paper, 7496 = live)
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` - Alert notifications
- `FINNHUB_API_KEY`, `ALPHA_VANTAGE_API_KEY` - News and market data

### Risk Management
- `MAX_RISK_PER_TRADE_PCT` - Default: 2.0 (max 2% account risk per trade)
- `MAX_DAILY_LOSS_PCT` - Default: 3.0 (stop trading after 3% daily loss)
- `MAX_PORTFOLIO_HEAT_PCT` - Default: 6.0 (max 6% total risk across all positions)
- `MIN_RISK_REWARD_RATIO` - Default: 2.0 (minimum 2:1 reward/risk ratio)

## Security Considerations

- **Never commit API keys, broker credentials, or webhook secrets** - Use environment variables
- **Webhook Authentication**: All TradingView webhooks must include `auth_token` validated against `TV_WEBHOOK_SECRET`
- **Trade Validation**: Multiple validation layers (News Shield, kill zones, risk rules) prevent erroneous trades
- **Interactive Brokers**: Use paper trading port (7497) during development; live port (7496) only for production
- **Kafka Authentication**: Enable SASL/SSL for production deployments
- **Rate Limiting**: Respect API limits (Finnhub: 60 calls/min, IBKR TWS: 50 msg/sec)

## Trading Knowledge Modules

The bot implements comprehensive trading strategies and risk management:

### Smart Money Concepts (ICT/SMC)
- Fair Value Gaps (FVG)
- Order Blocks & Breaker Blocks
- Liquidity Sweeps (Stop Hunts)
- Kill Zones (London, NY, Silver Bullet)
- Break of Structure (BOS) / Change of Character (CHoCH)
- Market Structure Shift (MSS)

### Technical Indicators (via ta4j)
- Momentum: RSI, MACD, Stochastic, CCI
- Trend: SMA/EMA, ADX, Parabolic SAR, Ichimoku
- Volatility: Bollinger Bands, ATR, Keltner Channels
- Volume: OBV, VWAP, Volume Profile, MFI

### Risk Management Rules
- Fixed fractional position sizing (1-2% per trade)
- Kelly Criterion (Quarter Kelly)
- ATR-based stop loss (1.5-2.5x multiplier)
- 2:1 minimum risk/reward ratio
- Portfolio heat monitoring (6% max)
- Daily loss limits (3% max)

## Testing Strategy

### Unit Tests
```bash
# Java services
./gradlew test

# Python service
cd news-shield && pytest
```

### Integration Tests
```bash
# Requires Docker services running
docker-compose up -d
./gradlew integrationTest
```

### Paper Trading
- Connect IBKR TWS with paper trading account (port 7497)
- Configure TradingView alerts with paper account webhook URL
- Monitor trades via Telegram bot and Grafana dashboards

## Deployment Notes

### Minimum Infrastructure Cost: $24.95/month
- TradingView Pro (for webhooks): $24.95/mo
- Oracle Cloud Free Tier VM: $0
- Self-hosted TimescaleDB, Kafka, Redis: $0
- Finnhub API (free tier): $0
- Telegram Bot: $0

### Production Deployment
- Use Docker Compose or Kubernetes for orchestration
- Enable SSL/TLS for all external endpoints
- Configure Cloudflare Tunnels or ngrok for webhook ingress
- Set up Prometheus alerting for system failures
- Enable database backups (pg_dump for TimescaleDB)

## Support & Resources

- **Interactive Brokers API Docs**: https://interactivebrokers.github.io/tws-api/
- **TradingView Pine Script Reference**: https://www.tradingview.com/pine-script-docs/
- **TimescaleDB Documentation**: https://docs.timescale.com/
- **Finnhub API**: https://finnhub.io/docs/api
- **ta4j Library**: https://ta4j.github.io/ta4j-wiki/

## Ticket Instructions

When working on a specific ticket:

1. **Read the complete ticket** in `tradie-implementation-guide.md` before starting
2. **Follow the implementation steps** in sequence
3. **Implement all acceptance criteria** listed in the ticket
4. **Write tests** as specified in the ticket's test cases
5. **Update documentation** if the ticket affects user-facing features
6. **Do not skip steps** - each step builds on the previous

When doing the ticket please do not make unnecessary changes, do not rename existing variables or methods unless it is necessary to do so. Please provide all the necessary changes needed to fully and correctly solve the ticket, please do not go outside the scope of the ticket unless it is important to do so, and if it is then please let me know. Please do not overcomplicate things, be clean and simple. Please go through the ticket carefully and make sure to fully understand it and complete it and all its requirements. Please explain your changes to me and how they fully solve the ticket. After you provide your changes please answer the questions below:
1. Have we fully and correctly solved the ticket and ALL its requirements?
2. Are there any wrong or unnecessary or unneeded changes made?
3. Are there any changes we forgot to make for the ticket?
4. Are we 100% good?
5. Have we maintained structure and functionality with the changes provided for the ticket?
6. Are the changes simple, clean, correct, and effective?
7. Have we made the changes in the correct place or places?

PLEASE DO NOT OVERLOOK ANYTHING AND MAKE SURE TO UNDERSTAND EVERYTHING. Please make sure to uphold the structure and format of how we do code when you provide the changes.

Give a short commit message of the changes made for the ticket. Then provide a step by step guide on how to test the changes made through our endpoints on postman and/or the mysql database if applicable.

Make sure the code is fully correct and the codebase is fully correct, and will pass the github actions pipelines we have in places.

## PR Review Instructions

When reviewing code changes or PRs for this project:

1. **Verify all trading logic is correct** - Incorrect calculations can lead to financial loss
2. **Check risk management rules** - Ensure position sizing, stop loss, and portfolio heat limits are enforced
3. **Validate security measures** - API keys must not be hardcoded; webhooks must be authenticated
4. **Ensure proper error handling** - Trading errors should never crash the system
5. **Check for proper logging** - All trades, signals, and errors must be logged for audit purposes
6. **Verify tests are comprehensive** - Trading logic must have high test coverage
7. **Confirm IBKR integration safety** - Paper trading should be used for development/testing

Please answer the questions below:
1. Have we fully and correctly solved the ticket and ALL its requirements?
2. Are there any wrong or unnecessary or unneeded changes made?
3. Are there any changes we forgot to make for the ticket?
4. Are we 100% good?
5. Have we maintained structure and functionality with the changes provided for the ticket?
6. Are the changes simple, clean, correct, and effective?
7. Have we made the changes in the correct place or places?

PLEASE DO NOT OVERLOOK ANYTHING AND MAKE SURE TO UNDERSTAND EVERYTHING.

Please do a thorough review. Do not overlook or assume anything. If you questions or concerns then please ask me. Capture every detail, do not overlook anything. Please let me know of any wrong changes made, any wrong functionality, let me know of anything and everything no matter how small. Please make sure to also check the structure and format of the code added and confirm if it aligns with the existing structure and format we have in our codebase. Provide the comments needed. Please we have to make sure all is well and clean before shipping. If everything is perfectly done then please confirm to me so. Also make sure all is following the DRY principle. Make sure we have not gone out of ticket scope.

## Comments Instructions

So we have some comments to work on for the pull request i made with the changes we made for the ticket, so please only work on a
comment if it is valid, correct, and important to implement. If the comment is NOT valid, incorrect, or unnecessary to implement, then do not do
the comment and provide a response as to why the comment is NOT valid, incorrect, or unnecessary to implement. Otherwise if the comment is valid,
correct, and important to implement, then implement it.

## More important instructions

- Remember to always make sure all the code in the codebase is correct and safe! That ALL pipelines pass. 
- If there are any configurations i need to make then please let me know. For example, database configurations, cloud configurations, etc. for integration with the project, then let me know. Basically anything that is beyond coding or your capabilities.
- Always write tests for your code.
- Always work on a ticket branch, never on the main branch.
- Please don't push to main branch.
- Ask me any questions you need for clarification.
