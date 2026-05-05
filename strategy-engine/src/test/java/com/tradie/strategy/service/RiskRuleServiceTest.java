package com.tradie.strategy.service;

import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.strategy.dto.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskRuleServiceTest {

    @Mock
    private DailyMetricsService dailyMetrics;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeSignalRepository signalRepository;

    private RiskRuleService service;

    @BeforeEach
    void setUp() {
        service = new RiskRuleService(dailyMetrics, positionRepository, signalRepository);
        ReflectionTestUtils.setField(service, "maxDailyLossPct", 3.0);
        ReflectionTestUtils.setField(service, "maxConcurrentPositions", 5);
        ReflectionTestUtils.setField(service, "maxPortfolioHeatPct", 6.0);
        ReflectionTestUtils.setField(service, "losingStreakThreshold", 3);
        ReflectionTestUtils.setField(service, "losingStreakSizeReduction", 0.5);
        ReflectionTestUtils.setField(service, "minRiskRewardRatio", 2.0);
        ReflectionTestUtils.setField(service, "defaultAccountBalance", 10000.0);
    }

    private TradeSignal buildSignal(String symbol, TradeSignal.SignalAction action,
                                    double entry, double sl, double tp) {
        TradeSignal s = new TradeSignal();
        s.setSymbol(symbol);
        s.setAction(action);
        s.setPrice(BigDecimal.valueOf(entry));
        s.setStopLoss(BigDecimal.valueOf(sl));
        s.setTakeProfit(BigDecimal.valueOf(tp));
        s.setStrategy("FVG");
        return s;
    }

    private void stubDefaults() {
        when(dailyMetrics.getDailyPnl()).thenReturn(BigDecimal.ZERO);
        when(dailyMetrics.getConsecutiveLosses()).thenReturn(0L);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());
        when(signalRepository.findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(List.of());
    }

    @Test
    void allRulesPass_whenDefaultConditions() {
        stubDefaults();
        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);

        List<RuleResult> results = service.validateAll(signal);

        assertTrue(results.stream().allMatch(RuleResult::passed));
    }

    @Test
    void dailyLossLimit_exceeded_returnsFail() {
        when(dailyMetrics.getDailyPnl()).thenReturn(BigDecimal.valueOf(-400)); // -4% of 10000
        when(positionRepository.countByStatus(any())).thenReturn(0L);
        when(positionRepository.findByStatus(any())).thenReturn(List.of());
        when(dailyMetrics.getConsecutiveLosses()).thenReturn(0L);
        when(signalRepository.findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(List.of());

        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult dailyLossResult = results.stream()
                .filter(r -> r.reason() != null && r.reason().contains("Daily loss limit"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Daily loss limit rule result not found"));
        assertFalse(dailyLossResult.passed());
    }

    @Test
    void maxConcurrentPositions_reached_returnsFail() {
        when(dailyMetrics.getDailyPnl()).thenReturn(BigDecimal.ZERO);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(5L);
        when(positionRepository.findByStatus(any())).thenReturn(List.of());
        when(dailyMetrics.getConsecutiveLosses()).thenReturn(0L);
        when(signalRepository.findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(List.of());

        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult posResult = results.stream()
                .filter(r -> r.reason() != null && r.reason().contains("Max concurrent positions"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Max concurrent positions rule result not found"));
        assertFalse(posResult.passed());
    }

    @Test
    void losingStreak_returnsPassWithSizeReduction() {
        when(dailyMetrics.getDailyPnl()).thenReturn(BigDecimal.ZERO);
        when(dailyMetrics.getConsecutiveLosses()).thenReturn(3L);
        when(positionRepository.countByStatus(any())).thenReturn(0L);
        when(positionRepository.findByStatus(any())).thenReturn(List.of());
        when(signalRepository.findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(List.of());

        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult streakResult = results.stream()
                .filter(r -> r.passed() && r.sizeAdjustmentFactor().isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Losing streak rule result not found"));
        assertEquals(0, BigDecimal.valueOf(0.5).compareTo(streakResult.sizeAdjustmentFactor().get()));
    }

    @Test
    void duplicateSignal_sameSymbolAndAction_returnsFail() {
        when(dailyMetrics.getDailyPnl()).thenReturn(BigDecimal.ZERO);
        when(dailyMetrics.getConsecutiveLosses()).thenReturn(0L);
        when(positionRepository.countByStatus(any())).thenReturn(0L);
        when(positionRepository.findByStatus(any())).thenReturn(List.of());

        TradeSignal existing = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);
        existing.setStatus(TradeSignal.SignalStatus.VALIDATED);
        when(signalRepository.findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(eq("AAPL"), any()))
                .thenReturn(List.of(existing));

        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 115);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult dupResult = results.stream()
                .filter(r -> r.reason() != null && r.reason().contains("Duplicate signal"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Duplicate signal rule result not found"));
        assertFalse(dupResult.passed());
    }

    @Test
    void minRiskReward_tooLow_returnsFail() {
        stubDefaults();
        // R/R = (110-100)/(100-95) = 10/5 = 2.0 → exactly at limit, should pass
        // R/R = (104-100)/(100-95) = 4/5 = 0.8 → fails
        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 104);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult rrResult = results.stream()
                .filter(r -> r.reason() != null && r.reason().contains("Risk/reward too low"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Risk/reward rule result not found"));
        assertFalse(rrResult.passed());
    }

    @Test
    void minRiskReward_exactlyAtMinimum_passes() {
        stubDefaults();
        // R/R = (110-100)/(100-95) = 2.0 → exactly meets minimum
        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 110);
        List<RuleResult> results = service.validateAll(signal);

        assertTrue(results.stream().allMatch(RuleResult::passed));
    }

    @Test
    void minRiskReward_noStopLoss_passes() {
        stubDefaults();
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("AAPL");
        signal.setAction(TradeSignal.SignalAction.BUY);
        signal.setPrice(BigDecimal.valueOf(100));
        // No stop loss set

        List<RuleResult> results = service.validateAll(signal);

        assertTrue(results.stream().allMatch(RuleResult::passed));
    }

    @Test
    void minRiskReward_invertedBuySetup_returnsFail() {
        stubDefaults();
        // BUY with tp below entry — inverted setup
        TradeSignal signal = buildSignal("AAPL", TradeSignal.SignalAction.BUY, 100, 95, 90);
        List<RuleResult> results = service.validateAll(signal);

        RuleResult rrResult = results.stream()
                .filter(r -> r.reason() != null && r.reason().contains("Invalid TP/SL direction"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Direction check rule result not found"));
        assertFalse(rrResult.passed());
    }
}
