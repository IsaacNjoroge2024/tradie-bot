package com.tradie.strategy.positioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FixedFractionalCalculator {

    @Value("${tradie.position-sizing.risk-per-trade-pct:2.0}")
    private double riskPerTradePct;

    @Value("${tradie.position-sizing.max-position-pct:10.0}")
    private double maxPositionPct;

    public BigDecimal calculate(BigDecimal entryPrice, BigDecimal stopLoss, BigDecimal accountValue) {
        BigDecimal riskAmount = calculateRiskAmount(accountValue);

        BigDecimal stopDistance = entryPrice.subtract(stopLoss).abs();
        if (stopDistance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        BigDecimal quantity = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        // Cap at max position size (e.g. 10% of account in one position)
        BigDecimal maxPositionValue = accountValue
                .multiply(BigDecimal.valueOf(maxPositionPct))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal maxQuantity = maxPositionValue.divide(entryPrice, 8, RoundingMode.HALF_UP);

        return quantity.min(maxQuantity);
    }

    public BigDecimal calculateRiskAmount(BigDecimal accountValue) {
        return accountValue
                .multiply(BigDecimal.valueOf(riskPerTradePct))
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }
}
