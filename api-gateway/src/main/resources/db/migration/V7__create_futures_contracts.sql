-- Creates the futures_contracts reference table for Ticket 16: Futures Support.
-- Seeded with common E-mini, crude oil, and gold contracts.

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

-- E-mini S&P 500 (ES) — June 2025 front month
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('ESM5', 'ES', '202506', 'CME', 50.0, 0.25, 12.50, '2025-06-20', 12000.0, 10800.0, TRUE, TRUE),
    ('ESU5', 'ES', '202509', 'CME', 50.0, 0.25, 12.50, '2025-09-19', 12000.0, 10800.0, TRUE, FALSE)
ON CONFLICT (full_symbol) DO NOTHING;

-- E-mini Nasdaq-100 (NQ) — June 2025 front month
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('NQM5', 'NQ', '202506', 'CME', 20.0, 0.25, 5.00, '2025-06-20', 18000.0, 16200.0, TRUE, TRUE),
    ('NQU5', 'NQ', '202509', 'CME', 20.0, 0.25, 5.00, '2025-09-19', 18000.0, 16200.0, TRUE, FALSE)
ON CONFLICT (full_symbol) DO NOTHING;

-- Micro E-mini S&P 500 (MES)
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('MESM5', 'MES', '202506', 'CME', 5.0, 0.25, 1.25, '2025-06-20', 1200.0, 1080.0, TRUE, TRUE)
ON CONFLICT (full_symbol) DO NOTHING;

-- Micro E-mini Nasdaq-100 (MNQ)
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('MNQM5', 'MNQ', '202506', 'CME', 2.0, 0.25, 0.50, '2025-06-20', 1800.0, 1620.0, TRUE, TRUE)
ON CONFLICT (full_symbol) DO NOTHING;

-- WTI Crude Oil (CL) — July 2025
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('CLN5', 'CL', '202507', 'NYMEX', 1000.0, 0.01, 10.00, '2025-06-20', 8000.0, 7200.0, TRUE, TRUE)
ON CONFLICT (full_symbol) DO NOTHING;

-- Gold (GC) — August 2025
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('GCQ5', 'GC', '202508', 'COMEX', 100.0, 0.10, 10.00, '2025-07-29', 10000.0, 9000.0, TRUE, TRUE)
ON CONFLICT (full_symbol) DO NOTHING;
