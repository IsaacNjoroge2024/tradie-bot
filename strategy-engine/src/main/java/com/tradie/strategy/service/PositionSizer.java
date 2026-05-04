package com.tradie.strategy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PositionSizer {

    @Value("${tradie.risk.max-risk-per-trade-pct:2.0}")
    private double maxRiskPerTradePct;

    @Value("${tradie.risk.default-account-balance:10000.0}")
    private double defaultAccountBalance;

    /**
     * Fixed fractional sizing: risk a fixed % of account balance per trade.
     * Ticket 7 will expand this with Kelly Criterion and ATR-based methods.
     */
    public BigDecimal calculateQuantity(BigDecimal entryPrice, BigDecimal stopLoss,
                                        BigDecimal sizeAdjustmentFactor) {
        if (stopLoss == null) {
            return BigDecimal.ONE;
        }

        BigDecimal riskAmount = BigDecimal.valueOf(defaultAccountBalance * maxRiskPerTradePct / 100.0);
        BigDecimal stopDistance = entryPrice.subtract(stopLoss).abs();

        if (stopDistance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        BigDecimal quantity = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        if (sizeAdjustmentFactor != null
                && sizeAdjustmentFactor.compareTo(BigDecimal.ONE) != 0) {
            quantity = quantity.multiply(sizeAdjustmentFactor).setScale(8, RoundingMode.HALF_UP);
        }

        return quantity.max(BigDecimal.ONE);
    }
}
