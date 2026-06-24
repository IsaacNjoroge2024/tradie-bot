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

-- ===========================================
-- Forex Reference Data (Ticket 15)
-- ===========================================
CREATE TABLE IF NOT EXISTS currency_pairs (
    symbol                     VARCHAR(10)       PRIMARY KEY,
    base_currency              VARCHAR(5)        NOT NULL,
    quote_currency             VARCHAR(5)        NOT NULL,
    pip_position               INT               NOT NULL CHECK (pip_position > 0),
    pip_value_per_standard_lot DOUBLE PRECISION  NOT NULL CHECK (pip_value_per_standard_lot > 0),
    min_lot_size               DOUBLE PRECISION  NOT NULL DEFAULT 0.01 CHECK (min_lot_size > 0),
    lot_step                   DOUBLE PRECISION  NOT NULL DEFAULT 0.01 CHECK (lot_step > 0),
    margin_rate                DOUBLE PRECISION  NOT NULL DEFAULT 0.02 CHECK (margin_rate > 0 AND margin_rate <= 1),
    category                   VARCHAR(20)       NOT NULL CHECK (category IN ('MAJOR', 'MINOR', 'EXOTIC'))
);

-- Major pairs
INSERT INTO currency_pairs (symbol, base_currency, quote_currency, pip_position, pip_value_per_standard_lot, min_lot_size, lot_step, margin_rate, category) VALUES
('EURUSD', 'EUR', 'USD', 4, 10.00, 0.01, 0.01, 0.02, 'MAJOR'),
('GBPUSD', 'GBP', 'USD', 4, 10.00, 0.01, 0.01, 0.02, 'MAJOR'),
('USDJPY', 'USD', 'JPY', 2,  6.67, 0.01, 0.01, 0.02, 'MAJOR'),
('USDCHF', 'USD', 'CHF', 4, 11.00, 0.01, 0.01, 0.02, 'MAJOR'),
('AUDUSD', 'AUD', 'USD', 4, 10.00, 0.01, 0.01, 0.02, 'MAJOR'),
('USDCAD', 'USD', 'CAD', 4,  7.35, 0.01, 0.01, 0.02, 'MAJOR'),
('NZDUSD', 'NZD', 'USD', 4, 10.00, 0.01, 0.01, 0.02, 'MAJOR');

-- Minor pairs
INSERT INTO currency_pairs (symbol, base_currency, quote_currency, pip_position, pip_value_per_standard_lot, min_lot_size, lot_step, margin_rate, category) VALUES
('EURGBP', 'EUR', 'GBP', 4, 12.60, 0.01, 0.01, 0.025, 'MINOR'),
('EURJPY', 'EUR', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('GBPJPY', 'GBP', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('EURCHF', 'EUR', 'CHF', 4, 11.00, 0.01, 0.01, 0.025, 'MINOR'),
('EURCAD', 'EUR', 'CAD', 4,  7.35, 0.01, 0.01, 0.025, 'MINOR'),
('EURAUD', 'EUR', 'AUD', 4, 10.00, 0.01, 0.01, 0.025, 'MINOR'),
('GBPCHF', 'GBP', 'CHF', 4, 11.00, 0.01, 0.01, 0.025, 'MINOR'),
('GBPAUD', 'GBP', 'AUD', 4, 10.00, 0.01, 0.01, 0.025, 'MINOR'),
('GBPCAD', 'GBP', 'CAD', 4,  7.35, 0.01, 0.01, 0.025, 'MINOR'),
('AUDJPY', 'AUD', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('CADJPY', 'CAD', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('CHFJPY', 'CHF', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('AUDCAD', 'AUD', 'CAD', 4,  7.35, 0.01, 0.01, 0.025, 'MINOR'),
('AUDCHF', 'AUD', 'CHF', 4, 11.00, 0.01, 0.01, 0.025, 'MINOR'),
('AUDNZD', 'AUD', 'NZD', 4, 10.00, 0.01, 0.01, 0.025, 'MINOR'),
('NZDJPY', 'NZD', 'JPY', 2,  6.67, 0.01, 0.01, 0.025, 'MINOR'),
('NZDCAD', 'NZD', 'CAD', 4,  7.35, 0.01, 0.01, 0.025, 'MINOR'),
('NZDCHF', 'NZD', 'CHF', 4, 11.00, 0.01, 0.01, 0.025, 'MINOR');

-- Exotic pairs (USD-base pip value = 10 / typical_price; real-time values computed by ForexPipCalculator)
INSERT INTO currency_pairs (symbol, base_currency, quote_currency, pip_position, pip_value_per_standard_lot, min_lot_size, lot_step, margin_rate, category) VALUES
('USDZAR', 'USD', 'ZAR', 4,  0.54, 0.01, 0.01, 0.05, 'EXOTIC'),
('USDMXN', 'USD', 'MXN', 4,  0.59, 0.01, 0.01, 0.05, 'EXOTIC'),
('USDSGD', 'USD', 'SGD', 4,  7.35, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDHKD', 'USD', 'HKD', 4,  1.28, 0.01, 0.01, 0.02, 'EXOTIC'),
('USDNOK', 'USD', 'NOK', 4,  0.95, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDSEK', 'USD', 'SEK', 4,  0.95, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDDKK', 'USD', 'DKK', 4,  1.43, 0.01, 0.01, 0.03, 'EXOTIC');

-- ===========================================
-- Futures Reference Data (Ticket 16)
-- ===========================================
CREATE TABLE IF NOT EXISTS futures_contracts (
    full_symbol          VARCHAR(20)       PRIMARY KEY,
    symbol               VARCHAR(10)       NOT NULL,
    contract_month       VARCHAR(10),
    exchange             VARCHAR(20)       NOT NULL,
    multiplier           DOUBLE PRECISION  NOT NULL,
    tick_size            DOUBLE PRECISION  NOT NULL,
    tick_value           DOUBLE PRECISION  NOT NULL,
    first_notice_date    DATE,
    last_trade_date      DATE,
    expiration_date      DATE,
    initial_margin       DOUBLE PRECISION,
    maintenance_margin   DOUBLE PRECISION,
    is_active            BOOLEAN           NOT NULL DEFAULT TRUE,
    is_front_month       BOOLEAN           NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_futures_symbol       ON futures_contracts (symbol);
CREATE INDEX IF NOT EXISTS idx_futures_front_month  ON futures_contracts (symbol, is_front_month);
CREATE INDEX IF NOT EXISTS idx_futures_active        ON futures_contracts (is_active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_futures_one_front_month_per_symbol
    ON futures_contracts (symbol)
    WHERE is_front_month = TRUE;

INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('ESU6',  'ES',  '202609', 'CME',    50.0,  0.25, 12.50, '2026-09-19', 12000.0, 10800.0, TRUE, TRUE),
    ('ESZ6',  'ES',  '202612', 'CME',    50.0,  0.25, 12.50, '2026-12-18', 12000.0, 10800.0, TRUE, FALSE),
    ('NQU6',  'NQ',  '202609', 'CME',    20.0,  0.25,  5.00, '2026-09-19', 18000.0, 16200.0, TRUE, TRUE),
    ('NQZ6',  'NQ',  '202612', 'CME',    20.0,  0.25,  5.00, '2026-12-18', 18000.0, 16200.0, TRUE, FALSE),
    ('MESU6', 'MES', '202609', 'CME',     5.0,  0.25,  1.25, '2026-09-19',  1200.0,  1080.0, TRUE, TRUE),
    ('MNQU6', 'MNQ', '202609', 'CME',     2.0,  0.25,  0.50, '2026-09-19',  1800.0,  1620.0, TRUE, TRUE),
    ('CLQ6',  'CL',  '202608', 'NYMEX', 1000.0, 0.01, 10.00, '2026-07-22',  8000.0,  7200.0, TRUE, TRUE),
    ('GCQ6',  'GC',  '202608', 'COMEX',  100.0, 0.10, 10.00, '2026-07-29', 10000.0,  9000.0, TRUE, TRUE)
ON CONFLICT (full_symbol) DO NOTHING;

-- ===========================================
-- Crypto Assets Reference Data (Ticket 17)
-- ===========================================
CREATE TABLE IF NOT EXISTS crypto_assets (
    symbol                VARCHAR(10)      PRIMARY KEY,
    name                  VARCHAR(50),
    min_order_size        NUMERIC(20, 8)   NOT NULL,
    size_increment        NUMERIC(20, 8)   NOT NULL,
    price_precision       INT              NOT NULL,
    volatility_multiplier DOUBLE PRECISION NOT NULL DEFAULT 3.0,
    available_on_ibkr     BOOLEAN          NOT NULL DEFAULT TRUE
);

INSERT INTO crypto_assets (symbol, name, min_order_size, size_increment, price_precision, volatility_multiplier, available_on_ibkr) VALUES
('BTC', 'Bitcoin',      0.0001, 0.0001, 2, 3.0, TRUE),
('ETH', 'Ethereum',     0.001,  0.001,  2, 3.5, TRUE),
('LTC', 'Litecoin',     0.01,   0.01,   2, 4.0, TRUE),
('BCH', 'Bitcoin Cash', 0.001,  0.001,  2, 4.0, TRUE)
ON CONFLICT (symbol) DO NOTHING;

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
