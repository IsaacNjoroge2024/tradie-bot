package com.tradie.strategy.positioning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FixedFractionalCalculatorTest {

    private FixedFractionalCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new FixedFractionalCalculator();
        ReflectionTestUtils.setField(calculator, "riskPerTradePct", 2.0);
        ReflectionTestUtils.setField(calculator, "maxPositionPct", 10.0);
    }

    @Test
    void calculate_cappedByMaxPositionSize() {
        // riskAmount = 10000 × 2% = 200
        // stopDistance = 100 - 95 = 5 → rawQty = 200/5 = 40
        // maxPositionValue = 10000 × 10% = 1000 → maxQty = 1000/100 = 10 ← cap applies
        BigDecimal qty = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(95), BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.valueOf(10).compareTo(qty));
    }

    @Test
    void calculate_belowCap_returnsRawQuantity() {
        // riskAmount = 10000 × 2% = 200
        // stopDistance = 1 - 0.5 = 0.5 → rawQty = 400
        // maxPositionValue = 1000 → maxQty = 1000/1 = 1000 → rawQty=400 is below cap
        BigDecimal qty = calculator.calculate(
                BigDecimal.valueOf(1), BigDecimal.valueOf(0.5), BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.valueOf(400).compareTo(qty));
    }

    @Test
    void calculate_zeroStopDistance_returnsOne() {
        BigDecimal qty = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.ONE.compareTo(qty));
    }

    @Test
    void calculate_shortPosition_usesAbsStopDistance() {
        // SELL: entry=100, stopLoss=105, |distance|=5 → rawQty=40, capped to 10
        BigDecimal qty = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.valueOf(10).compareTo(qty));
    }

    @Test
    void calculateRiskAmount_twoPercent_returnsCorrectAmount() {
        BigDecimal risk = calculator.calculateRiskAmount(BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.valueOf(200).compareTo(risk));
    }

    @Test
    void calculate_exactAtMaxPosition_returnsCap() {
        // stopDistance=0.01 → rawQty=20000, capped at maxQty=10
        BigDecimal qty = calculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(99.99), BigDecimal.valueOf(10000));

        assertEquals(0, BigDecimal.valueOf(10).compareTo(qty));
    }
}
