package com.tradie.strategy.forex;

import org.springframework.stereotype.Component;

/**
 * Calculates pip sizes and pip values for forex currency pairs.
 *
 * <ul>
 *   <li>Direct pairs  (e.g. EUR/USD, GBP/USD): quote = USD → pip value = pipSize × units</li>
 *   <li>Indirect pairs (e.g. USD/JPY, USD/CHF): base = USD  → pip value = pipSize × units / price</li>
 *   <li>Cross pairs   (e.g. EUR/JPY, GBP/CHF): neither base nor quote is USD → convert via price</li>
 * </ul>
 */
@Component
public class ForexPipCalculator {

    private static final int STANDARD_LOT_UNITS = 100_000;

    /**
     * Returns the pip size for a pair: 0.01 for JPY pairs, 0.0001 for all others.
     */
    public double getPipSize(String pair) {
        return isJpyPair(pair) ? 0.01 : 0.0001;
    }

    /**
     * Calculates the pip value in USD for the given lot size at the current price.
     *
     * @param pair         currency pair symbol (any format, e.g. "EURUSD", "EUR/USD")
     * @param lotSize      position size in standard lots (1.0 = 100,000 units)
     * @param currentPrice current market price
     * @return pip value in USD
     */
    public double getPipValue(String pair, double lotSize, double currentPrice) {
        String normalized = CurrencyPairService.normalizePair(pair);
        double pipSize = getPipSize(normalized);
        double lotUnits = lotSize * STANDARD_LOT_UNITS;
        String quoteCurrency = extractQuoteCurrency(normalized);

        if ("USD".equals(quoteCurrency)) {
            // Direct pairs: pip value is fixed regardless of price
            return pipSize * lotUnits;
        } else if (normalized.startsWith("USD")) {
            // Indirect pairs: divide by current price to convert quote currency to USD
            return (pipSize * lotUnits) / currentPrice;
        } else {
            // Cross pairs: use current price as proxy to convert to USD
            return convertToUsd(pipSize * lotUnits, currentPrice);
        }
    }

    /**
     * Returns the absolute number of pips between two prices for a pair.
     *
     * @param pair   currency pair symbol
     * @param price1 first price
     * @param price2 second price
     * @return number of pips (always positive)
     */
    public double calculatePips(String pair, double price1, double price2) {
        return Math.abs(price1 - price2) / getPipSize(pair);
    }

    /**
     * Returns true if JPY is the quote currency (pip = 0.01).
     */
    public boolean isJpyPair(String pair) {
        return CurrencyPairService.normalizePair(pair).endsWith("JPY");
    }

    private String extractQuoteCurrency(String normalizedPair) {
        return normalizedPair.length() >= 6 ? normalizedPair.substring(3, 6) : "";
    }

    // For cross pairs, current price is the exchange rate base→quote.
    // Dividing by price converts quote currency units to an approximate USD value.
    private double convertToUsd(double valueInQuoteCurrency, double currentPrice) {
        return valueInQuoteCurrency / currentPrice;
    }
}
