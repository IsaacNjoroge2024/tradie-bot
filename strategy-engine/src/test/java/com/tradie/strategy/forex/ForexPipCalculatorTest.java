package com.tradie.strategy.forex;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForexPipCalculatorTest {

    private ForexPipCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ForexPipCalculator();
    }

    // ─── getPipSize ───────────────────────────────────────────────────────────

    @Test
    void getPipSize_standardPair_returns0001() {
        assertThat(calculator.getPipSize("EURUSD")).isEqualTo(0.0001);
        assertThat(calculator.getPipSize("GBPUSD")).isEqualTo(0.0001);
        assertThat(calculator.getPipSize("AUDUSD")).isEqualTo(0.0001);
        assertThat(calculator.getPipSize("NZDUSD")).isEqualTo(0.0001);
    }

    @Test
    void getPipSize_jpyPair_returns001() {
        assertThat(calculator.getPipSize("USDJPY")).isEqualTo(0.01);
        assertThat(calculator.getPipSize("EURJPY")).isEqualTo(0.01);
        assertThat(calculator.getPipSize("GBPJPY")).isEqualTo(0.01);
    }

    @Test
    void getPipSize_slashFormatJpyPair_returns001() {
        assertThat(calculator.getPipSize("USD/JPY")).isEqualTo(0.01);
    }

    // ─── isJpyPair ────────────────────────────────────────────────────────────

    @Test
    void isJpyPair_jpyQuote_returnsTrue() {
        assertTrue(calculator.isJpyPair("USDJPY"));
        assertTrue(calculator.isJpyPair("EURJPY"));
        assertTrue(calculator.isJpyPair("EUR/JPY"));
    }

    @Test
    void isJpyPair_nonJpy_returnsFalse() {
        assertFalse(calculator.isJpyPair("EURUSD"));
        assertFalse(calculator.isJpyPair("GBPCHF"));
        assertFalse(calculator.isJpyPair("USDCAD"));
    }

    // ─── getPipValue ──────────────────────────────────────────────────────────

    @Test
    void getPipValue_directUsdPair_returns10PerStandardLot() {
        // EUR/USD: pip=0.0001, 1 lot=100K units → $10 per pip (fixed, price-independent)
        double pipValue = calculator.getPipValue("EURUSD", 1.0, 1.1000);
        assertThat(pipValue).isCloseTo(10.0, Offset.offset(0.001));
    }

    @Test
    void getPipValue_directUsdPair_sameAtDifferentPrices() {
        double at1_10 = calculator.getPipValue("GBPUSD", 1.0, 1.10);
        double at1_30 = calculator.getPipValue("GBPUSD", 1.0, 1.30);
        assertThat(at1_10).isCloseTo(at1_30, Offset.offset(0.001));
    }

    @Test
    void getPipValue_indirectJpyPair_calculatesCorrectly() {
        // USD/JPY at 150.00: pip=0.01, 1 lot=100K USD → 1000 JPY / 150 ≈ $6.67
        double pipValue = calculator.getPipValue("USDJPY", 1.0, 150.0);
        assertThat(pipValue).isCloseTo(6.67, Offset.offset(0.01));
    }

    @Test
    void getPipValue_halfLot_isHalfOfOneLot() {
        double oneLot  = calculator.getPipValue("EURUSD", 1.0, 1.1);
        double halfLot = calculator.getPipValue("EURUSD", 0.5, 1.1);
        assertThat(halfLot).isCloseTo(oneLot / 2.0, Offset.offset(0.001));
    }

    @Test
    void getPipValue_microLot_isOneHundredthOfStandardLot() {
        double standardLot = calculator.getPipValue("EURUSD", 1.0, 1.1);
        double microLot    = calculator.getPipValue("EURUSD", 0.01, 1.1);
        assertThat(microLot).isCloseTo(standardLot * 0.01, Offset.offset(0.0001));
    }

    // ─── calculatePips ────────────────────────────────────────────────────────

    @Test
    void calculatePips_standardPair_returnsCorrectPips() {
        // 1.1020 - 1.1000 = 0.0020 → 20 pips
        double pips = calculator.calculatePips("EURUSD", 1.1020, 1.1000);
        assertThat(pips).isCloseTo(20.0, Offset.offset(0.01));
    }

    @Test
    void calculatePips_jpyPair_returnsCorrectPips() {
        // 150.50 - 150.00 = 0.50 → 50 pips
        double pips = calculator.calculatePips("USDJPY", 150.50, 150.00);
        assertThat(pips).isCloseTo(50.0, Offset.offset(0.01));
    }

    @Test
    void calculatePips_reverseOrder_returnsAbsoluteValue() {
        double pips = calculator.calculatePips("EURUSD", 1.1000, 1.1020);
        assertThat(pips).isCloseTo(20.0, Offset.offset(0.01));
    }

    @Test
    void calculatePips_samePrices_returnsZero() {
        double pips = calculator.calculatePips("EURUSD", 1.1000, 1.1000);
        assertThat(pips).isEqualTo(0.0);
    }
}
