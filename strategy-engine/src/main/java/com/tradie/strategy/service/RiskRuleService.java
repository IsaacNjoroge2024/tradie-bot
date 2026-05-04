package com.tradie.strategy.service;

import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.strategy.dto.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskRuleService {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleService.class);

    private final DailyMetricsService dailyMetrics;
    private final PositionRepository positionRepository;
    private final TradeSignalRepository signalRepository;

    @Value("${tradie.risk.max-daily-loss-pct:3.0}")
    private double maxDailyLossPct;

    @Value("${tradie.strategy.max-concurrent-positions:5}")
    private int maxConcurrentPositions;

    @Value("${tradie.risk.max-portfolio-heat-pct:6.0}")
    private double maxPortfolioHeatPct;

    @Value("${tradie.risk.losing-streak-threshold:3}")
    private int losingStreakThreshold;

    @Value("${tradie.risk.losing-streak-size-reduction:0.5}")
    private double losingStreakSizeReduction;

    @Value("${tradie.risk.min-risk-reward-ratio:2.0}")
    private double minRiskRewardRatio;

    @Value("${tradie.risk.default-account-balance:10000.0}")
    private double defaultAccountBalance;

    public RiskRuleService(
            DailyMetricsService dailyMetrics,
            PositionRepository positionRepository,
            TradeSignalRepository signalRepository) {
        this.dailyMetrics = dailyMetrics;
        this.positionRepository = positionRepository;
        this.signalRepository = signalRepository;
    }

    public List<RuleResult> validateAll(TradeSignal signal) {
        List<RuleResult> results = new ArrayList<>();

        results.add(checkDailyLossLimit());
        results.add(checkMaxConcurrentPositions());
        results.add(checkPortfolioHeat());
        results.add(checkLosingStreak());
        results.add(checkDuplicateSignal(signal));
        results.add(checkMinRiskReward(signal));

        results.forEach(r -> {
            if (!r.passed()) {
                log.warn("Risk rule FAILED for signal {}: {}", signal.getId(), r.reason());
            } else {
                log.debug("Risk rule PASSED for signal {}: adjustment={}",
                        signal.getId(), r.sizeAdjustmentFactor().orElse(null));
            }
        });

        return results;
    }

    private RuleResult checkDailyLossLimit() {
        BigDecimal dailyPnl = dailyMetrics.getDailyPnl();
        BigDecimal lossLimit = BigDecimal.valueOf(-defaultAccountBalance * maxDailyLossPct / 100.0);

        if (dailyPnl.compareTo(lossLimit) < 0) {
            return RuleResult.fail(String.format(
                    "Daily loss limit reached: P&L=%.2f, limit=%.2f", dailyPnl, lossLimit));
        }
        return RuleResult.pass();
    }

    private RuleResult checkMaxConcurrentPositions() {
        long openCount = positionRepository.countByStatus(Position.PositionStatus.OPEN);
        if (openCount >= maxConcurrentPositions) {
            return RuleResult.fail(String.format(
                    "Max concurrent positions reached: %d/%d", openCount, maxConcurrentPositions));
        }
        return RuleResult.pass();
    }

    private RuleResult checkPortfolioHeat() {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        BigDecimal totalRisk = openPositions.stream()
                .filter(p -> p.getStopLoss() != null)
                .map(p -> {
                    BigDecimal stopDistance = p.getEntryPrice().subtract(p.getStopLoss()).abs();
                    return stopDistance.multiply(p.getQuantity());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal accountBalance = BigDecimal.valueOf(defaultAccountBalance);
        BigDecimal heatPct = totalRisk.divide(accountBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (heatPct.compareTo(BigDecimal.valueOf(maxPortfolioHeatPct)) > 0) {
            return RuleResult.fail(String.format(
                    "Portfolio heat too high: %.2f%% (max %.2f%%)", heatPct, maxPortfolioHeatPct));
        }
        return RuleResult.pass();
    }

    private RuleResult checkLosingStreak() {
        long consecutiveLosses = dailyMetrics.getConsecutiveLosses();
        if (consecutiveLosses >= losingStreakThreshold) {
            BigDecimal factor = BigDecimal.valueOf(losingStreakSizeReduction);
            log.warn("Losing streak detected ({} losses) - reducing position size by {}%",
                    consecutiveLosses, (1.0 - losingStreakSizeReduction) * 100);
            return RuleResult.passWithAdjustment(factor);
        }
        return RuleResult.pass();
    }

    private RuleResult checkDuplicateSignal(TradeSignal signal) {
        Instant fiveMinutesAgo = Instant.now().minusSeconds(300);
        List<TradeSignal> recent = signalRepository
                .findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(signal.getSymbol(), fiveMinutesAgo);

        boolean duplicate = recent.stream()
                .filter(s -> s.getId() == null || !s.getId().equals(signal.getId()))
                .anyMatch(s -> s.getAction() == signal.getAction()
                        && s.getStatus() != TradeSignal.SignalStatus.REJECTED
                        && s.getStatus() != TradeSignal.SignalStatus.EXPIRED);

        if (duplicate) {
            return RuleResult.fail(String.format(
                    "Duplicate signal: %s %s within last 5 minutes",
                    signal.getAction(), signal.getSymbol()));
        }
        return RuleResult.pass();
    }

    private RuleResult checkMinRiskReward(TradeSignal signal) {
        if (signal.getStopLoss() == null || signal.getTakeProfit() == null) {
            return RuleResult.pass();
        }

        BigDecimal entry = signal.getPrice();
        BigDecimal sl = signal.getStopLoss();
        BigDecimal tp = signal.getTakeProfit();

        BigDecimal reward = tp.subtract(entry).abs();
        BigDecimal risk = entry.subtract(sl).abs();

        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return RuleResult.fail("Invalid stop loss: equals entry price");
        }

        BigDecimal rr = reward.divide(risk, 4, RoundingMode.HALF_UP);
        if (rr.compareTo(BigDecimal.valueOf(minRiskRewardRatio)) < 0) {
            return RuleResult.fail(String.format(
                    "Risk/reward too low: %.2f (min %.1f)", rr, minRiskRewardRatio));
        }
        return RuleResult.pass();
    }
}
