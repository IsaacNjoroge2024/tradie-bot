-- Creates the currency_pairs reference table for Ticket 15: Forex Support.
-- Populated with major, minor, and exotic pairs.
-- pip_value_per_standard_lot is an approximate USD reference value;
-- real-time values are computed dynamically by ForexPipCalculator.

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
('NZDUSD', 'NZD', 'USD', 4, 10.00, 0.01, 0.01, 0.02, 'MAJOR')
ON CONFLICT (symbol) DO NOTHING;

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
('NZDCHF', 'NZD', 'CHF', 4, 11.00, 0.01, 0.01, 0.025, 'MINOR')
ON CONFLICT (symbol) DO NOTHING;

-- Exotic pairs (USD-base pip value = 10 / typical_price; real-time values computed by ForexPipCalculator)
INSERT INTO currency_pairs (symbol, base_currency, quote_currency, pip_position, pip_value_per_standard_lot, min_lot_size, lot_step, margin_rate, category) VALUES
('USDZAR', 'USD', 'ZAR', 4,  0.54, 0.01, 0.01, 0.05, 'EXOTIC'),
('USDMXN', 'USD', 'MXN', 4,  0.59, 0.01, 0.01, 0.05, 'EXOTIC'),
('USDSGD', 'USD', 'SGD', 4,  7.35, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDHKD', 'USD', 'HKD', 4,  1.28, 0.01, 0.01, 0.02, 'EXOTIC'),
('USDNOK', 'USD', 'NOK', 4,  0.95, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDSEK', 'USD', 'SEK', 4,  0.95, 0.01, 0.01, 0.03, 'EXOTIC'),
('USDDKK', 'USD', 'DKK', 4,  1.43, 0.01, 0.01, 0.03, 'EXOTIC')
ON CONFLICT (symbol) DO NOTHING;
