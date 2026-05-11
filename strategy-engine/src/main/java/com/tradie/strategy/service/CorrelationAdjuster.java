package com.tradie.strategy.service;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class CorrelationAdjuster {

    private static final Logger log = LoggerFactory.getLogger(CorrelationAdjuster.class);

    // Threshold at which correlation adjustments begin
    private static final double CORR_UNCORRELATED = 0.3;
    // Threshold at which the high-correlation (50%) reduction kicks in
    private static final double CORR_HIGH = 0.7;
    // Default assumed correlation for unknown symbol pairs (conservative)
    private static final double CORR_UNKNOWN_DEFAULT = 0.5;
    // Size reduction for medium correlation (0.3 ≤ ρ < 0.7): reduce by 25%
    private static final double FACTOR_MEDIUM = 0.75;
    // Size reduction for high correlation (ρ ≥ 0.7): reduce by 50%
    private static final double FACTOR_HIGH = 0.50;

    // Known correlation pairs; bidirectional lookup handled in getCorrelation()
    private static final Map<String, Double> CORRELATION_PAIRS = Map.of(
            "SPY:QQQ", 0.85,
            "AAPL:MSFT", 0.75,
            "EUR/USD:GBP/USD", 0.80,
            "BTC:ETH", 0.85
    );

    private final PositionRepository positionRepository;

    public CorrelationAdjuster(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /**
     * Adjusts position size based on correlation with existing open positions.
     *
     * Thresholds:
     *  ρ < 0.3    → no adjustment (uncorrelated)
     *  0.3 ≤ ρ < 0.7 → reduce by 25% (medium correlation)
     *  ρ ≥ 0.7   → reduce by 50% (high correlation)
     */
    public BigDecimal adjust(String newSymbol, BigDecimal quantity, List<String> adjustments) {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        if (openPositions.isEmpty()) return quantity;

        double maxCorrelation = openPositions.stream()
                .mapToDouble(p -> getCorrelation(newSymbol, p.getSymbol()))
                .max()
                .orElse(0.0);

        if (maxCorrelation < CORR_UNCORRELATED) {
            return quantity;
        }

        double factor = maxCorrelation >= CORR_HIGH ? FACTOR_HIGH : FACTOR_MEDIUM;
        BigDecimal adjusted = quantity.multiply(BigDecimal.valueOf(factor))
                .setScale(8, RoundingMode.HALF_UP);

        log.debug("Correlation adjustment for {}: ρ={}, factor={}", newSymbol, maxCorrelation, factor);
        adjustments.add(String.format(
                "Correlation adjustment for %s: ρ=%.2f, size reduced by %.0f%%",
                newSymbol, maxCorrelation, (1.0 - factor) * 100));

        return adjusted;
    }

    private double getCorrelation(String symbol1, String symbol2) {
        if (symbol1.equalsIgnoreCase(symbol2)) return 1.0;

        String key = symbol1 + ":" + symbol2;
        String reverseKey = symbol2 + ":" + symbol1;

        return CORRELATION_PAIRS.getOrDefault(key,
                CORRELATION_PAIRS.getOrDefault(reverseKey, CORR_UNKNOWN_DEFAULT));
    }
}
