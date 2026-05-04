-- Index for fast lookups by symbol + time (used by strategy-engine duplicate signal check)
CREATE INDEX idx_signals_symbol_created_at
    ON trade_signals (symbol, created_at DESC);
