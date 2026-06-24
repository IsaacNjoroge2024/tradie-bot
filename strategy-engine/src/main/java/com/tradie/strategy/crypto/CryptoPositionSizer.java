package com.tradie.strategy.crypto;

import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import com.tradie.strategy.crypto.dto.CryptoPositionSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calculates crypto position sizes with volatility adjustment.
 *
 * <p>Formula: adjustedRiskPct = riskPct / volatilityMultiplier
 * quantity = (accountBalance × adjustedRiskPct / 100) / |entry - stop|
 * quantity is rounded down to the asset's size increment and floored at minOrderSize.
 */
@Component
public class CryptoPositionSizer {

    private static final Logger log = LoggerFactory.getLogger(CryptoPositionSizer.class);

    private final CryptoAssetService assetService;

    public CryptoPositionSizer(CryptoAssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * Calculates the optimal fractional quantity for a crypto trade.
     *
     * @param symbol         crypto symbol (e.g., "BTC")
     * @param accountBalance account balance in USD
     * @param riskPct        base risk percentage (e.g., 2.0 for 2%)
     * @param entryPrice     entry price in USD
     * @param stopLossPrice  stop-loss price in USD
     * @return calculated position size details
     * @throws IllegalArgumentException if the symbol is not supported on IBKR
     */
    public CryptoPositionSize calculate(String symbol, double accountBalance,
                                         double riskPct, double entryPrice, double stopLossPrice) {
        CryptoAssetSpec spec = assetService.getAssetSpecOrDefault(symbol);

        if (!spec.availableOnIbkr()) {
            throw new IllegalArgumentException("Crypto asset not available on IBKR: " + symbol);
        }

        double volMultiplier = spec.volatilityMultiplier();
        double adjustedRiskPct = riskPct / volMultiplier;
        double riskAmount = accountBalance * (adjustedRiskPct / 100.0);

        double priceRisk = Math.abs(entryPrice - stopLossPrice);

        double quantity;
        if (priceRisk <= 0) {
            quantity = spec.minOrderSize();
        } else {
            quantity = riskAmount / priceRisk;
        }

        quantity = roundToIncrement(quantity, spec.sizeIncrement());
        quantity = Math.max(quantity, spec.minOrderSize());

        double notionalValue = quantity * entryPrice;

        log.debug("Crypto sizing for {}: volMult={}, adjustedRisk={}%, qty={}, notional={}",
                symbol, volMultiplier, adjustedRiskPct, quantity, notionalValue);

        return new CryptoPositionSize(quantity, notionalValue, riskAmount, adjustedRiskPct, symbol);
    }

    /**
     * Rounds a quantity down to the nearest valid size increment.
     */
    public double roundToIncrement(double quantity, double increment) {
        if (increment <= 0) return quantity;
        return Math.floor(quantity / increment) * increment;
    }

    /**
     * Formats a quantity to the precision appropriate for the given symbol.
     */
    public String formatQuantity(String symbol, double quantity) {
        CryptoAssetSpec spec = assetService.getAssetSpecOrDefault(symbol);
        return String.format("%." + spec.pricePrecision() + "f", quantity);
    }
}
