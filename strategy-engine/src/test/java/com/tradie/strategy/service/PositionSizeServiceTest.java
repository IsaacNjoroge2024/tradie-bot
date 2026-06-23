package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.AccountInfo;
import com.tradie.strategy.dto.PositionSizeResult;
import com.tradie.strategy.forex.ForexPipCalculator;
import com.tradie.strategy.forex.ForexPositionSizer;
import com.tradie.strategy.forex.dto.ForexPositionSize;
import com.tradie.strategy.futures.FuturesPositionSizer;
import com.tradie.strategy.futures.InsufficientMarginException;
import com.tradie.strategy.futures.dto.FuturesPositionSize;
import com.tradie.strategy.positioning.ATRPositionSizeCalculator;
import com.tradie.strategy.positioning.FixedFractionalCalculator;
import com.tradie.strategy.positioning.KellyCriterionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionSizeServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private PortfolioHeatService portfolioHeatService;

    @Mock
    private FixedFractionalCalculator fixedFractionalCalculator;

    @Mock
    private KellyCriterionCalculator kellyCriterionCalculator;

    @Mock
    private ATRPositionSizeCalculator atrCalculator;

    @Mock
    private CorrelationAdjuster correlationAdjuster;

    @Mock
    private ForexPipCalculator forexPipCalculator;

    @Mock
    private ForexPositionSizer forexPositionSizer;

    @Mock
    private FuturesPositionSizer futuresPositionSizer;

    private PositionSizeService service;

    private static final AccountInfo DEFAULT_ACCOUNT = new AccountInfo(
            BigDecimal.valueOf(10000), BigDecimal.valueOf(10000),
            BigDecimal.valueOf(10000), BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        service = new PositionSizeService(
                accountService, portfolioHeatService, fixedFractionalCalculator,
                kellyCriterionCalculator, atrCalculator, correlationAdjuster,
                new ObjectMapper(), forexPipCalculator, forexPositionSizer, futuresPositionSizer);

        ReflectionTestUtils.setField(service, "defaultSizingMethod", "FIXED_FRACTIONAL");
        ReflectionTestUtils.setField(service, "riskPerTradePct", 2.0);
        ReflectionTestUtils.setField(service, "warningHeatPct", 4.0);
        ReflectionTestUtils.setField(service, "reduceHeatPct", 5.0);

        when(accountService.getAccountInfo()).thenReturn(DEFAULT_ACCOUNT);
        when(portfolioHeatService.getCurrentHeatPct()).thenReturn(0.0);
        lenient().when(fixedFractionalCalculator.calculateRiskAmount(any()))
                .thenReturn(BigDecimal.valueOf(200));
        // Return quantity unchanged for correlation (pass-through)
        when(correlationAdjuster.adjust(anyString(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private TradeSignal buildSignal() {
        TradeSignal s = new TradeSignal();
        s.setSymbol("AAPL");
        s.setExchange("NASDAQ");
        s.setStrategy("FVG");
        s.setPrice(BigDecimal.valueOf(100));
        s.setStopLoss(BigDecimal.valueOf(95));
        s.setTakeProfit(BigDecimal.valueOf(115));
        s.setAction(TradeSignal.SignalAction.BUY);
        return s;
    }

    @Test
    void calculatePositionSize_fixedFractional_returnsValidResult() {
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals("FIXED_FRACTIONAL", result.sizingMethod());
        assertEquals(0, BigDecimal.valueOf(40).compareTo(result.quantity()));
        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.riskAmount()));
        assertEquals(2.0, result.riskPercentage(), 0.01);
        assertEquals(0.0, result.portfolioHeatBefore());
        assertEquals(2.0, result.portfolioHeatAfter(), 0.01);
    }

    @Test
    void calculatePositionSize_kellyCriterionMethod_usesKellyCalculator() {
        ReflectionTestUtils.setField(service, "defaultSizingMethod", "KELLY");
        when(kellyCriterionCalculator.calculate(any(), any(), any(), anyString()))
                .thenReturn(Optional.of(BigDecimal.valueOf(35)));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals("KELLY", result.sizingMethod());
        assertEquals(0, BigDecimal.valueOf(35).compareTo(result.quantity()));
        verify(fixedFractionalCalculator, never()).calculate(any(), any(), any());
    }

    @Test
    void calculatePositionSize_kellyInsufficientData_fallsBackToFixed() {
        ReflectionTestUtils.setField(service, "defaultSizingMethod", "KELLY");
        when(kellyCriterionCalculator.calculate(any(), any(), any(), anyString()))
                .thenReturn(Optional.empty());
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals(0, BigDecimal.valueOf(40).compareTo(result.quantity()));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("Fixed Fractional")));
    }

    @Test
    void calculatePositionSize_atrMethodNoAtr_fallsBackToFixed() {
        ReflectionTestUtils.setField(service, "defaultSizingMethod", "ATR");
        when(atrCalculator.calculate(any(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals(0, BigDecimal.valueOf(40).compareTo(result.quantity()));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("Fixed Fractional")));
    }

    @Test
    void calculatePositionSize_highHeat_reducesSize() {
        when(portfolioHeatService.getCurrentHeatPct()).thenReturn(5.5);
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        // qty=40 reduced by 50% due to heat=5.5%
        assertEquals(0, BigDecimal.valueOf(20).compareTo(result.quantity()));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("heat")));
    }

    @Test
    void calculatePositionSize_warningHeat_addsWarningButNoReduction() {
        when(portfolioHeatService.getCurrentHeatPct()).thenReturn(4.5);
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        // qty=40, no size reduction at warning level
        assertEquals(0, BigDecimal.valueOf(40).compareTo(result.quantity()));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("warning")));
    }

    @Test
    void calculatePositionSize_externalAdjustment_appliesReduction() {
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(
                buildSignal(), BigDecimal.valueOf(0.5));

        // qty=40 × 0.5 = 20
        assertEquals(0, BigDecimal.valueOf(20).compareTo(result.quantity()));
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("Risk rule size adjustment")));
    }

    @Test
    void calculatePositionSize_cryptoExchange_allowsFractionalQuantity() {
        TradeSignal signal = buildSignal();
        signal.setExchange("CRYPTO");
        signal.setSymbol("BTC");
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(0.00123456));

        PositionSizeResult result = service.calculatePositionSize(signal, BigDecimal.ONE);

        assertTrue(result.valid());
        assertTrue(result.quantity().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculatePositionSize_noStopLoss_fallsToFallback() {
        TradeSignal signal = buildSignal();
        signal.setStopLoss(null);

        PositionSizeResult result = service.calculatePositionSize(signal, BigDecimal.ONE);

        assertTrue(result.valid());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("No valid stop loss")));
    }

    @Test
    void calculatePositionSize_zeroAdjustmentFactor_invalidNotBumpedToOne() {
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        // sizeAdjustmentFactor=0 drives quantity to zero — must not be silently floored to 1
        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ZERO);

        assertFalse(result.valid());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("fell to zero")));
    }

    @Test
    void calculatePositionSize_portfolioHeatFields_populated() {
        when(portfolioHeatService.getCurrentHeatPct()).thenReturn(2.0);
        when(fixedFractionalCalculator.calculate(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(40));

        PositionSizeResult result = service.calculatePositionSize(buildSignal(), BigDecimal.ONE);

        assertEquals(2.0, result.portfolioHeatBefore(), 0.001);
        assertEquals(4.0, result.portfolioHeatAfter(), 0.01); // 2% existing + 2% new risk
    }

    @Test
    void calculatePositionSize_futuresSignal_usesFuturesPositionSizer() {
        TradeSignal futuresSignal = new TradeSignal();
        futuresSignal.setSymbol("ES");
        futuresSignal.setExchange("CME");
        futuresSignal.setStrategy("FVG");
        futuresSignal.setPrice(BigDecimal.valueOf(5000.00));
        futuresSignal.setStopLoss(BigDecimal.valueOf(4990.00));
        futuresSignal.setTakeProfit(BigDecimal.valueOf(5020.00));
        futuresSignal.setAction(TradeSignal.SignalAction.BUY);

        FuturesPositionSize futuresSize = new FuturesPositionSize(2, 500000.0, 200.0, 40.0, 24000.0);
        when(futuresPositionSizer.calculate(eq("ES"), eq(10000.0), eq(2.0),
                eq(5000.0), eq(4990.0)))
                .thenReturn(futuresSize);

        PositionSizeResult result = service.calculatePositionSize(futuresSignal, BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals("FUTURES", result.assetClass());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(result.quantity()));
        verify(futuresPositionSizer).calculate(eq("ES"), eq(10000.0), eq(2.0), eq(5000.0), eq(4990.0));
    }

    @Test
    void calculatePositionSize_futuresInsufficientMargin_returnsInvalid() {
        TradeSignal futuresSignal = new TradeSignal();
        futuresSignal.setSymbol("ES");
        futuresSignal.setExchange("CME");
        futuresSignal.setStrategy("FVG");
        futuresSignal.setPrice(BigDecimal.valueOf(5000.00));
        futuresSignal.setStopLoss(BigDecimal.valueOf(4990.00));
        futuresSignal.setTakeProfit(BigDecimal.valueOf(5020.00));
        futuresSignal.setAction(TradeSignal.SignalAction.BUY);

        when(futuresPositionSizer.calculate(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new InsufficientMarginException("Required margin exceeds buffer"));

        PositionSizeResult result = service.calculatePositionSize(futuresSignal, BigDecimal.ONE);

        assertFalse(result.valid());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("margin validation failed")));
    }

    @Test
    void calculatePositionSize_crossPairForex_fallsToZeroAndInvalid() {
        TradeSignal crossPairSignal = new TradeSignal();
        crossPairSignal.setSymbol("EURGBP");
        crossPairSignal.setExchange("FOREX");
        crossPairSignal.setStrategy("FVG");
        crossPairSignal.setPrice(BigDecimal.valueOf(0.86));
        crossPairSignal.setStopLoss(BigDecimal.valueOf(0.84));
        crossPairSignal.setTakeProfit(BigDecimal.valueOf(0.90));
        crossPairSignal.setAction(TradeSignal.SignalAction.BUY);

        when(forexPipCalculator.calculatePips(eq("EURGBP"), eq(0.86), eq(0.84)))
                .thenReturn(200.0);
        when(forexPositionSizer.calculate(eq("EURGBP"), eq(10000.0), eq(2.0), eq(0.86), eq(200.0)))
                .thenThrow(new IllegalArgumentException(
                        "Cross-pair pip value requires a quote-to-USD conversion rate: EURGBP"));

        PositionSizeResult result = service.calculatePositionSize(crossPairSignal, BigDecimal.ONE);

        assertFalse(result.valid());
        assertTrue(result.adjustments().stream().anyMatch(a -> a.contains("unsupported pair")));
    }

    @Test
    void calculatePositionSize_forexSignal_usesForexPositionSizer() {
        TradeSignal forexSignal = new TradeSignal();
        forexSignal.setSymbol("EURUSD");
        forexSignal.setExchange("FOREX");
        forexSignal.setStrategy("FVG");
        forexSignal.setPrice(BigDecimal.valueOf(1.10));
        forexSignal.setStopLoss(BigDecimal.valueOf(1.08));
        forexSignal.setTakeProfit(BigDecimal.valueOf(1.14));
        forexSignal.setAction(TradeSignal.SignalAction.BUY);

        when(forexPipCalculator.calculatePips(eq("EURUSD"), eq(1.10), eq(1.08)))
                .thenReturn(20.0);
        ForexPositionSize forexSize =
                new ForexPositionSize(0.5, 50000.0, "MINI", 200.0, 5.0, 1100.0);
        when(forexPositionSizer.calculate(
                eq("EURUSD"), eq(10000.0), eq(2.0), eq(1.10), eq(20.0)))
                .thenReturn(forexSize);

        PositionSizeResult result = service.calculatePositionSize(forexSignal, BigDecimal.ONE);

        assertTrue(result.valid());
        assertEquals("FOREX", result.assetClass());
        verify(forexPositionSizer).calculate(
                eq("EURUSD"), eq(10000.0), eq(2.0), eq(1.10), eq(20.0));
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(result.quantity()));
    }
}
