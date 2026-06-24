-- Creates the crypto_assets reference table for Ticket 17: Cryptocurrency Support via IBKR.
-- Seeded with supported IBKR Paxos crypto assets (BTC, ETH, LTC, BCH).
-- volatility_multiplier indicates how many times more volatile the asset is vs stocks
-- and is used to proportionally reduce position size.

CREATE TABLE IF NOT EXISTS crypto_assets (
    symbol               VARCHAR(10)       PRIMARY KEY,
    name                 VARCHAR(50),
    min_order_size       DOUBLE PRECISION  NOT NULL,
    size_increment       DOUBLE PRECISION  NOT NULL,
    price_precision      INT               NOT NULL,
    volatility_multiplier DOUBLE PRECISION NOT NULL DEFAULT 3.0,
    available_on_ibkr    BOOLEAN           NOT NULL DEFAULT TRUE
);

INSERT INTO crypto_assets (symbol, name, min_order_size, size_increment, price_precision, volatility_multiplier, available_on_ibkr) VALUES
('BTC', 'Bitcoin',      0.0001, 0.0001, 2, 3.0, TRUE),
('ETH', 'Ethereum',     0.001,  0.001,  2, 3.5, TRUE),
('LTC', 'Litecoin',     0.01,   0.01,   2, 4.0, TRUE),
('BCH', 'Bitcoin Cash', 0.001,  0.001,  2, 4.0, TRUE)
ON CONFLICT (symbol) DO NOTHING;
