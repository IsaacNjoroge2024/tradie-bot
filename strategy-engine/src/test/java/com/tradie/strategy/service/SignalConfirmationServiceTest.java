package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.IndicatorSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.BaseBarSeries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalConfirmationServiceTest {

    @Mock
    private OHLCVDataService ohlcvDataService;

    @Mock
    private IndicatorCalculatorService indicatorCalculatorService;

    @Mock
    private DivergenceDetector divergenceDetector;

    private SignalConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new SignalConfirmationService(ohlcvDataService, indicatorCalculatorService,
                divergenceDetector);
        ReflectionTestUtils.setField(service, "confirmationEnabled", true);
        ReflectionTestUtils.setField(service, "minConfirmations", 2);
        ReflectionTestUtils.setField(service, "divergenceBoostPct", 15.0);
        ReflectionTestUtils.setField(service, "defaultTimeframe", "1H");
        ReflectionTestUtils.setField(service, "defaultExchange", "NASDAQ");
    }

    private TradeSignal buildBuySignal() {
        TradeSignal s = new TradeSignal();
        s.setSymbol("AAPL");
        s.setExchange("NASDAQ");
        s.setTimeframe("1H");
        s.setAction(TradeSignal.SignalAction.BUY);
        s.setConfidenceScore(60.0);
        s.setPrice(BigDecimal.valueOf(150));
        s.setStrategy("FVG");
        return s;
    }

    private IndicatorSnapshot buildBullishSnapshot() {
        // RSI 45 (not overbought), price 155 > EMA50 150, ADX 30 (trending), MACD > signal
        return new IndicatorSnapshot(
                "AAPL", "1H", Instant.now(),
                155.0, 154.0, 156.0, 153.0,
                152.0, 151.0, 150.0, 145.0, "BULLISH",
                45.0, "NEUTRAL",
                0.5, 0.3, 0.2,
                30.0, 25.0, 15.0, "TRENDING",
                2.0, 160.0, 150.0, 140.0, 20.0,
                1000.0, 900.0, 1.1
        );
    }

    private IndicatorSnapshot buildBearishSnapshot() {
        // RSI 75 (overbought), price 145 < EMA50 150, ADX 30, MACD < signal
        return new IndicatorSnapshot(
                "AAPL", "1H", Instant.now(),
                145.0, 146.0, 147.0, 144.0,
                148.0, 149.0, 150.0, 155.0, "BEARISH",
                75.0, "OVERBOUGHT",
                0.3, 0.5, -0.2,
                30.0, 15.0, 25.0, "TRENDING",
                2.0, 155.0, 150.0, 145.0, 10.0,
                1000.0, 900.0, 1.1
        );
    }

    private BaseBarSeries buildSeries() {
        BaseBarSeries series = new BaseBarSeries("AAPL");
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < 30; i++) {
            series.addBar(java.time.Duration.ofHours(1), now.minusHours(30 - i),
                    100.0 + i, 102.0 + i, 99.0 + i, 101.0 + i, 1000.0);
        }
        return series;
    }

    @Test
    void confirm_buyWithAllIndicatorsAligned_returnsConfirmed() {
        BaseBarSeries series = buildSeries();
        when(ohlcvDataService.getBarSeries(eq("AAPL"), anyString(), anyString())).thenReturn(series);
        when(indicatorCalculatorService.calculateLatest(any(), eq("AAPL"), anyString()))
                .thenReturn(buildBullishSnapshot());
        when(divergenceDetector.detect(any(), eq("AAPL"), anyString()))
                .thenReturn(new DivergenceDetector.DivergenceResult(false, false));

        ConfirmationResult result = service.confirm(buildBuySignal());

        assertTrue(result.confirmed());
        assertEquals(4, result.confirmationCount());
        assertEquals(4, result.totalIndicators());
        assertFalse(result.confirmingIndicators().isEmpty());
        assertTrue(result.conflictingIndicators().isEmpty());
    }

    @Test
    void confirm_buyWithConflictingIndicators_notConfirmed() {
        BaseBarSeries series = buildSeries();
        when(ohlcvDataService.getBarSeries(eq("AAPL"), anyString(), anyString())).thenReturn(series);
        when(indicatorCalculatorService.calculateLatest(any(), eq("AAPL"), anyString()))
                .thenReturn(buildBearishSnapshot());
        when(divergenceDetector.detect(any(), eq("AAPL"), anyString()))
                .thenReturn(new DivergenceDetector.DivergenceResult(false, false));

        ConfirmationResult result = service.confirm(buildBuySignal());

        assertFalse(result.confirmed());
        assertTrue(result.confirmationCount() < 2);
    }

    @Test
    void confirm_withBullishDivergence_boostsConfidence() {
        BaseBarSeries series = buildSeries();
        when(ohlcvDataService.getBarSeries(any(), any(), any())).thenReturn(series);
        when(indicatorCalculatorService.calculateLatest(any(), any(), any()))
                .thenReturn(buildBullishSnapshot());
        when(divergenceDetector.detect(any(), any(), any()))
                .thenReturn(new DivergenceDetector.DivergenceResult(true, false));

        ConfirmationResult result = service.confirm(buildBuySignal());

        // Base 60 + 4*10 + 15 = 115, capped at 100
        assertEquals(100.0, result.adjustedConfidence(), 0.001);
    }

    @Test
    void confirm_whenDisabled_returnsNoOpResult() {
        ReflectionTestUtils.setField(service, "confirmationEnabled", false);
        TradeSignal signal = buildBuySignal();

        ConfirmationResult result = service.confirm(signal);

        assertTrue(result.confirmed());
        assertEquals(0, result.totalIndicators());
        assertEquals(60.0, result.adjustedConfidence(), 0.001);
        verifyNoInteractions(ohlcvDataService, indicatorCalculatorService);
    }

    @Test
    void confirm_whenOhlcvUnavailable_returnsNoOpResult() {
        when(ohlcvDataService.getBarSeries(any(), any(), any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        ConfirmationResult result = service.confirm(buildBuySignal());

        assertTrue(result.confirmed());
        assertEquals(0, result.totalIndicators());
        verifyNoInteractions(indicatorCalculatorService);
    }

    @Test
    void confirm_whenSeriesEmpty_returnsNoOpResult() {
        when(ohlcvDataService.getBarSeries(any(), any(), any()))
                .thenReturn(new BaseBarSeries("EMPTY"));

        ConfirmationResult result = service.confirm(buildBuySignal());

        assertTrue(result.confirmed());
        assertEquals(0, result.totalIndicators());
    }

    @Test
    void confirm_confidenceCappedAt100() {
        BaseBarSeries series = buildSeries();
        when(ohlcvDataService.getBarSeries(any(), any(), any())).thenReturn(series);
        when(indicatorCalculatorService.calculateLatest(any(), any(), any()))
                .thenReturn(buildBullishSnapshot());
        when(divergenceDetector.detect(any(), any(), any()))
                .thenReturn(new DivergenceDetector.DivergenceResult(true, false));

        TradeSignal signal = buildBuySignal();
        signal.setConfidenceScore(95.0);

        ConfirmationResult result = service.confirm(signal);

        assertTrue(result.adjustedConfidence() <= 100.0);
    }

    @Test
    void confirm_confidenceNotBelowZero() {
        BaseBarSeries series = buildSeries();
        when(ohlcvDataService.getBarSeries(any(), any(), any())).thenReturn(series);
        when(indicatorCalculatorService.calculateLatest(any(), any(), any()))
                .thenReturn(buildBearishSnapshot());
        when(divergenceDetector.detect(any(), any(), any()))
                .thenReturn(new DivergenceDetector.DivergenceResult(false, false));

        TradeSignal signal = buildBuySignal();
        signal.setConfidenceScore(5.0);

        ConfirmationResult result = service.confirm(signal);

        assertTrue(result.adjustedConfidence() >= 0.0);
    }
}
