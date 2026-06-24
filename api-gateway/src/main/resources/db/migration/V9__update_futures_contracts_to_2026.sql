-- Deactivate all stale 2025 contracts before inserting new front months.
-- The is_front_month update must happen first to avoid violating the partial unique
-- index (uq_futures_one_front_month_per_symbol) added in V8.
UPDATE futures_contracts
SET is_active        = FALSE,
    is_front_month   = FALSE
WHERE full_symbol IN ('ESM5', 'ESU5', 'NQM5', 'NQU5', 'MESM5', 'MNQM5', 'CLN5', 'GCQ5');

-- Insert current-cycle (2026) contracts.
-- Expiration dates:
--   E-mini / Micro equity index: third Friday of September 2026 = 2026-09-19
--   CL August 2026 (CLQ6):       third business day before July 25 = 2026-07-22
--   GC August 2026 (GCQ6):       2026-07-29 (consistent with CME gold convention)
--   December back months:         third Friday of December 2026 = 2026-12-18
INSERT INTO futures_contracts
    (full_symbol, symbol, contract_month, exchange, multiplier, tick_size, tick_value,
     expiration_date, initial_margin, maintenance_margin, is_active, is_front_month)
VALUES
    ('ESU6',  'ES',  '202609', 'CME',    50.0,   0.25, 12.50, '2026-09-19', 12000.0, 10800.0, TRUE,  TRUE),
    ('ESZ6',  'ES',  '202612', 'CME',    50.0,   0.25, 12.50, '2026-12-18', 12000.0, 10800.0, TRUE,  FALSE),
    ('NQU6',  'NQ',  '202609', 'CME',    20.0,   0.25,  5.00, '2026-09-19', 18000.0, 16200.0, TRUE,  TRUE),
    ('NQZ6',  'NQ',  '202612', 'CME',    20.0,   0.25,  5.00, '2026-12-18', 18000.0, 16200.0, TRUE,  FALSE),
    ('MESU6', 'MES', '202609', 'CME',     5.0,   0.25,  1.25, '2026-09-19',  1200.0,  1080.0, TRUE,  TRUE),
    ('MNQU6', 'MNQ', '202609', 'CME',     2.0,   0.25,  0.50, '2026-09-19',  1800.0,  1620.0, TRUE,  TRUE),
    ('CLQ6',  'CL',  '202608', 'NYMEX', 1000.0,  0.01, 10.00, '2026-07-22',  8000.0,  7200.0, TRUE,  TRUE),
    ('GCQ6',  'GC',  '202608', 'COMEX',  100.0,  0.10, 10.00, '2026-07-29', 10000.0,  9000.0, TRUE,  TRUE)
ON CONFLICT (full_symbol) DO UPDATE SET
    is_active          = EXCLUDED.is_active,
    is_front_month     = EXCLUDED.is_front_month,
    expiration_date    = EXCLUDED.expiration_date,
    initial_margin     = EXCLUDED.initial_margin,
    maintenance_margin = EXCLUDED.maintenance_margin;
