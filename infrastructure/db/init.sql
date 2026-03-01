-- =============================================================
-- Tradie Bot - Database Initialization
-- TimescaleDB schema: OHLCV, signals, orders, positions,
-- economic events, news sentiment, audit log, system config.
-- =============================================================

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- ===========================================
-- OHLCV Price Data (Hypertable)
-- ===========================================
CREATE TABLE ohlcv (
    time        TIMESTAMPTZ      NOT NULL,
    symbol      VARCHAR(20)      NOT NULL,
    exchange    VARCHAR(20)      NOT NULL,
    timeframe   VARCHAR(10)      NOT NULL,  -- '1m', '5m', '15m', '1h', '4h', '1d'
    open        DOUBLE PRECISION NOT NULL,
    high        DOUBLE PRECISION NOT NULL,
    low         DOUBLE PRECISION NOT NULL,
    close       DOUBLE PRECISION NOT NULL,
    volume      BIGINT           NOT NULL,

    PRIMARY KEY (time, symbol, exchange, timeframe)
);

SELECT create_hypertable('ohlcv', 'time');

ALTER TABLE ohlcv SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'symbol, exchange, timeframe'
);
SELECT add_compression_policy('ohlcv', INTERVAL '7 days');
SELECT add_retention_policy('ohlcv', INTERVAL '2 years');

CREATE INDEX idx_ohlcv_symbol_time    ON ohlcv (symbol, time DESC);
CREATE INDEX idx_ohlcv_exchange_symbol ON ohlcv (exchange, symbol);

-- ===========================================
-- Trade Signals Table
-- Enum columns use VARCHAR for JPA @Enumerated(EnumType.STRING) compatibility.
-- Monetary/price fields use NUMERIC(20,8) for financial precision.
-- ===========================================
CREATE TABLE trade_signals (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    symbol           VARCHAR(20)  NOT NULL,
    exchange         VARCHAR(20)  NOT NULL,
    action           VARCHAR(20)  NOT NULL,             -- SignalAction enum
    strategy         VARCHAR(50)  NOT NULL,
    source           VARCHAR(20)  NOT NULL,             -- SignalSource enum
    price            NUMERIC(20,8)            NOT NULL,
    stop_loss        NUMERIC(20,8),
    take_profit      NUMERIC(20,8),
    confidence_score DOUBLE PRECISION,
    timeframe        VARCHAR(10),
    status           VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',  -- SignalStatus enum
    rejection_reason TEXT,
    raw_payload      JSONB,

    processed_at TIMESTAMPTZ,
    executed_at  TIMESTAMPTZ
);

CREATE INDEX idx_signals_status      ON trade_signals (status);
CREATE INDEX idx_signals_symbol_time ON trade_signals (symbol, created_at DESC);
CREATE INDEX idx_signals_strategy    ON trade_signals (strategy);

-- ===========================================
-- Orders Table
-- ===========================================
CREATE TABLE orders (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    signal_id       UUID         REFERENCES trade_signals(id) ON DELETE SET NULL,
    ib_order_id     INTEGER,
    ib_perm_id      BIGINT,

    symbol          VARCHAR(20)  NOT NULL,
    exchange        VARCHAR(20)  NOT NULL,
    asset_class     VARCHAR(20)  NOT NULL,  -- 'STK', 'CASH', 'FUT', 'CRYPTO'
    side            VARCHAR(20)  NOT NULL,  -- OrderSide enum
    order_type      VARCHAR(20)  NOT NULL,  -- OrderType enum
    quantity        NUMERIC(20,8)            NOT NULL,
    limit_price     NUMERIC(20,8),
    stop_price      NUMERIC(20,8),

    parent_order_id   UUID REFERENCES orders(id) ON DELETE SET NULL,
    is_bracket_parent BOOLEAN      DEFAULT FALSE,

    status           VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',  -- OrderStatus enum
    filled_quantity  NUMERIC(20,8)            DEFAULT 0,
    avg_fill_price   NUMERIC(20,8),
    commission       NUMERIC(20,8),

    submitted_at TIMESTAMPTZ,
    filled_at    TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_orders_signal ON orders (signal_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_ib_id  ON orders (ib_order_id);

-- ===========================================
-- Positions Table
-- ===========================================
CREATE TABLE positions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    opened_at  TIMESTAMPTZ  NOT NULL    DEFAULT NOW(),
    closed_at  TIMESTAMPTZ,

    symbol      VARCHAR(20)  NOT NULL,
    exchange    VARCHAR(20)  NOT NULL,
    asset_class VARCHAR(20)  NOT NULL,
    side        VARCHAR(20)  NOT NULL,  -- OrderSide enum
    quantity    NUMERIC(20,8)            NOT NULL,
    entry_price NUMERIC(20,8)            NOT NULL,
    exit_price  NUMERIC(20,8),

    stop_loss         NUMERIC(20,8),
    take_profit       NUMERIC(20,8),
    trailing_stop_pct DOUBLE PRECISION,

    realized_pnl    NUMERIC(20,8),
    unrealized_pnl  NUMERIC(20,8),
    commission_total NUMERIC(20,8)        DEFAULT 0,

    strategy         VARCHAR(50),
    entry_signal_id  UUID REFERENCES trade_signals(id) ON DELETE SET NULL,
    exit_signal_id   UUID REFERENCES trade_signals(id) ON DELETE SET NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'  -- PositionStatus enum
);

CREATE INDEX idx_positions_status ON positions (status);
CREATE INDEX idx_positions_symbol ON positions (symbol);

-- ===========================================
-- Economic Events Table (for News Shield)
-- ===========================================
CREATE TABLE economic_events (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_time TIMESTAMPTZ  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    country    VARCHAR(3)   NOT NULL,
    currency   VARCHAR(5),
    impact     VARCHAR(10)  NOT NULL,  -- LOW, MEDIUM, HIGH
    forecast   VARCHAR(50),
    previous   VARCHAR(50),
    actual     VARCHAR(50),

    processed BOOLEAN DEFAULT FALSE,

    PRIMARY KEY (id, event_time)
);

SELECT create_hypertable('economic_events', 'event_time');
CREATE INDEX idx_events_time_impact ON economic_events (event_time, impact);

-- ===========================================
-- News Sentiment Table
-- ===========================================
CREATE TABLE news_sentiment (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    published_at TIMESTAMPTZ NOT NULL,
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    source   VARCHAR(50) NOT NULL,
    headline TEXT        NOT NULL,
    summary  TEXT,
    url      TEXT,

    sentiment_score DOUBLE PRECISION,  -- -1.0 to 1.0
    sentiment_label VARCHAR(20),       -- 'POSITIVE', 'NEGATIVE', 'NEUTRAL'

    symbols VARCHAR(20)[],

    PRIMARY KEY (id, published_at)
);

SELECT create_hypertable('news_sentiment', 'published_at');
CREATE INDEX idx_news_symbols ON news_sentiment USING GIN (symbols);

-- ===========================================
-- Trade Audit Log (Hypertable)
-- ===========================================
CREATE TABLE audit_log (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    time        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    service     VARCHAR(50)  NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    details     JSONB,
    user_id     VARCHAR(50),

    PRIMARY KEY (id, time)
);

SELECT create_hypertable('audit_log', 'time');

-- ===========================================
-- System Configuration
-- ===========================================
CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       JSONB        NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

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
    "london_open":   {"start": "02:00", "end": "05:00", "timezone": "America/New_York"},
    "ny_open":       {"start": "08:30", "end": "11:00", "timezone": "America/New_York"},
    "silver_bullet": {"start": "10:00", "end": "11:00", "timezone": "America/New_York"},
    "ny_afternoon":  {"start": "13:00", "end": "15:00", "timezone": "America/New_York"}
}', 'ICT Kill Zone timing configuration'),

('high_impact_events', '{
    "nfp":  {"pause_before_min": 30, "pause_after_min": 60},
    "fomc": {"pause_before_min": 30, "pause_after_min": 60},
    "cpi":  {"pause_before_min": 15, "pause_after_min": 45},
    "gdp":  {"pause_before_min": 15, "pause_after_min": 30}
}', 'News Shield event pause configuration');
