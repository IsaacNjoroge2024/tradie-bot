package com.tradie.strategy.positioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class ATRPositionSizeCalculator {

    private static final Logger log = LoggerFactory.getLogger(ATRPositionSizeCalculator.class);

    @Value("${tradie.atr.default-multiplier:2.0}")
    private double defaultMultiplier;

    @Value("${tradie.atr.scalping-multiplier:1.5}")
    private double scalpingMultiplier;

    @Value("${tradie.atr.swing-multiplier:2.5}")
    private double swingMultiplier;

    private final FixedFractionalCalculator fixedFractionalCalculator;

    public ATRPositionSizeCalculator(FixedFractionalCalculator fixedFractionalCalculator) {
        this.fixedFractionalCalculator = fixedFractionalCalculator;
    }

    /**
     * Calculates quantity using ATR-based sizing.
     * Returns empty if atrValue is zero or negative (signals fallback to Fixed Fractional).
     *
     * Formula: positionSize = dollarRisk / (ATR × atrMultiplier)
     */
    public Optional<BigDecimal> calculate(BigDecimal accountValue, double atrValue, String strategy) {
        if (atrValue <= 0) {
            log.debug("ATR not available (value={}), skipping ATR sizing", atrValue);
            return Optional.empty();
        }

        double multiplier = resolveMultiplier(strategy);
        BigDecimal dollarRisk = fixedFractionalCalculator.calculateRiskAmount(accountValue);
        BigDecimal stopDistance = BigDecimal.valueOf(atrValue * multiplier);

        if (stopDistance.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal quantity = dollarRisk.divide(stopDistance, 8, RoundingMode.HALF_UP);
        log.debug("ATR sizing: atr={}, multiplier={}, dollarRisk={}, qty={}",
                atrValue, multiplier, dollarRisk, quantity);

        return Optional.of(quantity);
    }

    private double resolveMultiplier(String strategy) {
        if (strategy == null) return defaultMultiplier;
        String upper = strategy.toUpperCase();
        if (upper.contains("SCALP")) return scalpingMultiplier;
        if (upper.contains("SWING")) return swingMultiplier;
        return defaultMultiplier;
    }
}
