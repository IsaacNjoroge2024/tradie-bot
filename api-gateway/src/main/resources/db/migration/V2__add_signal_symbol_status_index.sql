-- Composite index for fast lookups by symbol + status + time (used by strategy-engine)
CREATE INDEX idx_signals_symbol_status_time
    ON trade_signals (symbol, status, created_at DESC);
