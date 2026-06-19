-- Migrate trade_journal financial columns from DOUBLE PRECISION to NUMERIC(20,8)
-- to match the rest of the schema (trade_signals, orders, positions) and prevent
-- floating-point rounding errors in financial data.
ALTER TABLE trade_journal
    ALTER COLUMN entry_price      TYPE NUMERIC(20,8) USING entry_price::NUMERIC,
    ALTER COLUMN exit_price       TYPE NUMERIC(20,8) USING exit_price::NUMERIC,
    ALTER COLUMN quantity         TYPE NUMERIC(20,8) USING quantity::NUMERIC,
    ALTER COLUMN realized_pnl     TYPE NUMERIC(20,8) USING realized_pnl::NUMERIC,
    ALTER COLUMN realized_pnl_pct TYPE NUMERIC(20,8) USING realized_pnl_pct::NUMERIC,
    ALTER COLUMN commissions      TYPE NUMERIC(20,8) USING commissions::NUMERIC;
