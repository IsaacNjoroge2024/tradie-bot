-- Trade Journal table for post-trade analysis and performance review
CREATE TABLE trade_journal (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id      UUID         REFERENCES positions(id) ON DELETE SET NULL,

    symbol           VARCHAR(20)  NOT NULL,
    side             VARCHAR(10)  NOT NULL,
    entry_time       TIMESTAMPTZ  NOT NULL,
    exit_time        TIMESTAMPTZ,
    entry_price      DOUBLE PRECISION NOT NULL,
    exit_price       DOUBLE PRECISION,
    quantity         DOUBLE PRECISION NOT NULL,

    realized_pnl     DOUBLE PRECISION,
    realized_pnl_pct DOUBLE PRECISION,
    commissions      DOUBLE PRECISION,

    strategy         VARCHAR(50),
    timeframe        VARCHAR(10),
    signal_source    VARCHAR(20),

    setup_quality    VARCHAR(20),
    execution_quality VARCHAR(20),
    notes            TEXT,
    tags             TEXT,

    market_condition VARCHAR(50),
    news_context     TEXT,
    entry_chart_url  TEXT,
    exit_chart_url   TEXT,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_symbol     ON trade_journal (symbol);
CREATE INDEX idx_journal_strategy   ON trade_journal (strategy);
CREATE INDEX idx_journal_entry_time ON trade_journal (entry_time DESC);
CREATE INDEX idx_journal_position   ON trade_journal (position_id);
