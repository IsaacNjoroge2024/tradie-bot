package com.tradie.strategy.positioning;

import com.tradie.common.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KellyCriterionCalculatorTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private FixedFractionalCalculator fallback;

    private KellyCriterionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new KellyCriterionCalculator(positionRepository, fallback);
        ReflectionTestUtils.setField(calculator, "kellyFraction", 0.25);
        ReflectionTestUtils.setField(calculator, "minTradesRequired", 30);
        ReflectionTestUtils.setField(calculator, "lookbackDays", 90);
    }

    private List<Object[]> rowOf(long wins, long losses, double avgWin, double avgLoss) {
        return Collections.singletonList(new Object[]{wins, losses, avgWin, avgLoss});
    }

    @Test
    void calculate_sufficientData_returnsKellyQuantity() {
        // winRate = 60/100 = 0.6, avgWin=300, avgLoss=150
        // kellyPct = 0.6 - (0.4 / (300/150)) = 0.6 - 0.2 = 0.4
        // fractionalKelly = 0.4 × 0.25 = 0.1
        // riskAmount = 10000 × 0.1 = 1000
        // quantity = 1000 / 5 = 200
        when(positionRepository.findStrategyKellyStats(eq("FVG"), any()))
                .thenReturn(rowOf(60L, 40L, 300.0, 150.0));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(95),
                BigDecimal.valueOf(10000), "FVG");

        assertTrue(result.isPresent());
        assertTrue(result.get().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculate_insufficientTrades_returnsEmpty() {
        // Only 10 trades total, minimum is 30
        when(positionRepository.findStrategyKellyStats(eq("FVG"), any()))
                .thenReturn(rowOf(6L, 4L, 300.0, 150.0));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(95),
                BigDecimal.valueOf(10000), "FVG");

        assertFalse(result.isPresent());
    }

    @Test
    void calculate_noRows_returnsEmpty() {
        when(positionRepository.findStrategyKellyStats(eq("FVG"), any()))
                .thenReturn(List.of());

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(95),
                BigDecimal.valueOf(10000), "FVG");

        assertFalse(result.isPresent());
    }

    @Test
    void calculate_negativeKelly_returnsEmpty() {
        // winRate = 30/100 = 0.3, avgWin=100, avgLoss=300
        // kellyPct = 0.3 - (0.7 / (100/300)) = 0.3 - 2.1 = -1.8 (negative)
        when(positionRepository.findStrategyKellyStats(eq("FVG"), any()))
                .thenReturn(rowOf(30L, 70L, 100.0, 300.0));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(95),
                BigDecimal.valueOf(10000), "FVG");

        assertFalse(result.isPresent());
    }

    @Test
    void calculate_zeroStopDistance_returnsEmpty() {
        when(positionRepository.findStrategyKellyStats(eq("FVG"), any()))
                .thenReturn(rowOf(60L, 40L, 300.0, 150.0));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                BigDecimal.valueOf(10000), "FVG");

        assertFalse(result.isPresent());
    }
}
