-- Enforces at most one active front-month contract per root symbol.
-- findBySymbolAndActiveTrueAndFrontMonthTrue() assumes a single row, so this
-- partial unique index guards that invariant at the DB layer.

CREATE UNIQUE INDEX IF NOT EXISTS uq_futures_one_front_month_per_symbol
    ON futures_contracts (symbol)
    WHERE is_front_month = TRUE;
