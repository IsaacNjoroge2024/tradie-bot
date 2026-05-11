package com.tradie.strategy.positioning;

import com.tradie.common.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class KellyCriterionCalculator {

    private static final Logger log = LoggerFactory.getLogger(KellyCriterionCalculator.class);

    @Value("${tradie.kelly.fraction:0.25}")
    private double kellyFraction;

    @Value("${tradie.kelly.min-trades-required:30}")
    private int minTradesRequired;

    @Value("${tradie.kelly.lookback-days:90}")
    private int lookbackDays;

    private final PositionRepository positionRepository;

    public KellyCriterionCalculator(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /**
     * Calculates quantity using Kelly Criterion.
     * Returns Optional.empty() if insufficient trade history or negative Kelly percentage;
     * the caller is responsible for falling back to an alternative sizing method.
     *
     * Formula: kellyPct = winRate - ((1 - winRate) / (avgWin / avgLoss))
     * Applied with fractional Kelly for safety: kellyPct × kellyFraction
     */
    public Optional<BigDecimal> calculate(BigDecimal entryPrice, BigDecimal stopLoss,
                                          BigDecimal accountValue, String strategy) {
        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<Object[]> rows = positionRepository.findStrategyKellyStats(strategy, cutoff);

        if (rows.isEmpty()) {
            log.debug("No Kelly stats rows returned for strategy={}", strategy);
            return Optional.empty();
        }

        Object[] row = rows.get(0);
        long wins = ((Number) row[0]).longValue();
        long losses = ((Number) row[1]).longValue();
        double avgWin = ((Number) row[2]).doubleValue();
        double avgLoss = ((Number) row[3]).doubleValue();

        long totalTrades = wins + losses;
        if (totalTrades < minTradesRequired || avgWin <= 0 || avgLoss <= 0) {
            log.debug("Insufficient Kelly data for strategy={}: trades={}, avgWin={}, avgLoss={}",
                    strategy, totalTrades, avgWin, avgLoss);
            return Optional.empty();
        }

        double winRate = (double) wins / totalTrades;
        double kellyPct = winRate - ((1.0 - winRate) / (avgWin / avgLoss));

        if (kellyPct <= 0) {
            log.warn("Negative Kelly percentage ({}) for strategy={}, falling back", kellyPct, strategy);
            return Optional.empty();
        }

        double fractionalKellyPct = kellyPct * kellyFraction;
        BigDecimal riskAmount = accountValue
                .multiply(BigDecimal.valueOf(fractionalKellyPct))
                .setScale(8, RoundingMode.HALF_UP);

        BigDecimal stopDistance = entryPrice.subtract(stopLoss).abs();
        if (stopDistance.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal quantity = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);
        log.debug("Kelly sizing for strategy={}: winRate={}, kellyPct={}, fractional={}, qty={}",
                strategy, winRate, kellyPct, fractionalKellyPct, quantity);

        return Optional.of(quantity);
    }
}
