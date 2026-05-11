package com.tradie.strategy.positioning;

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
class ATRPositionSizeCalculatorTest {

    @Mock
    private FixedFractionalCalculator fixedFractionalCalculator;

    private ATRPositionSizeCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ATRPositionSizeCalculator(fixedFractionalCalculator);
        ReflectionTestUtils.setField(calculator, "defaultMultiplier", 2.0);
        ReflectionTestUtils.setField(calculator, "scalpingMultiplier", 1.5);
        ReflectionTestUtils.setField(calculator, "swingMultiplier", 2.5);
    }

    @Test
    void calculate_withValidAtr_returnsCorrectQuantity() {
        // dollarRisk=200, atr=5, multiplier=2.0 → stopDistance=10 → qty=20
        when(fixedFractionalCalculator.calculateRiskAmount(any()))
                .thenReturn(BigDecimal.valueOf(200));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(10000), 5.0, "FVG");

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(20).compareTo(result.get()));
    }

    @Test
    void calculate_zeroAtr_returnsEmptyWithoutCallingCalculator() {
        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(10000), 0.0, "FVG");

        assertFalse(result.isPresent());
        verify(fixedFractionalCalculator, never()).calculateRiskAmount(any());
    }

    @Test
    void calculate_negativeAtr_returnsEmptyWithoutCallingCalculator() {
        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(10000), -1.0, "FVG");

        assertFalse(result.isPresent());
        verify(fixedFractionalCalculator, never()).calculateRiskAmount(any());
    }

    @Test
    void calculate_scalpStrategy_usesScalpingMultiplier() {
        // dollarRisk=200, atr=5, multiplier=1.5 → stopDistance=7.5 → qty≈26.67
        when(fixedFractionalCalculator.calculateRiskAmount(any()))
                .thenReturn(BigDecimal.valueOf(200));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(10000), 5.0, "SCALP_FVG");

        assertTrue(result.isPresent());
        // 200 / (5 × 1.5) = 200/7.5 ≈ 26.67
        assertTrue(result.get().compareTo(BigDecimal.valueOf(26)) > 0);
        assertTrue(result.get().compareTo(BigDecimal.valueOf(27)) < 0);
    }

    @Test
    void calculate_swingStrategy_usesSwingMultiplier() {
        // dollarRisk=200, atr=5, multiplier=2.5 → stopDistance=12.5 → qty=16
        when(fixedFractionalCalculator.calculateRiskAmount(any()))
                .thenReturn(BigDecimal.valueOf(200));

        Optional<BigDecimal> result = calculator.calculate(
                BigDecimal.valueOf(10000), 5.0, "SWING_ORDER_BLOCK");

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(16).compareTo(result.get()));
    }
}
