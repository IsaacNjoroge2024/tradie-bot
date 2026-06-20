package com.tradie.strategy.forex;

import com.tradie.common.entity.CurrencyPair;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexMarginCalculatorTest {

    @Mock
    private CurrencyPairService currencyPairService;

    private ForexMarginCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ForexMarginCalculator(currencyPairService);
    }

    // ���── calculateMargin ───��──────────────────────────────────────────────────

    @Test
    void calculateMargin_standardLot_returnsCorrectMargin() {
        // 1 lot EUR/USD at 1.10, 2% margin rate
        // Notional = 1 × 100,000 × 1.10 = $110,000 → margin = $2,200
        when(currencyPairService.getPairOrDefault("EURUSD")).thenReturn(buildPair("EURUSD", 0.02));

        double margin = calculator.calculateMargin("EURUSD", 1.0, 1.10);

        assertThat(margin).isCloseTo(2200.0, Offset.offset(0.01));
    }

    @Test
    void calculateMargin_microLot_isProportional() {
        when(currencyPairService.getPairOrDefault("EURUSD")).thenReturn(buildPair("EURUSD", 0.02));

        double standardMargin = calculator.calculateMargin("EURUSD", 1.0, 1.10);
        double microMargin    = calculator.calculateMargin("EURUSD", 0.01, 1.10);

        assertThat(microMargin).isCloseTo(standardMargin * 0.01, Offset.offset(0.01));
    }

    @Test
    void calculateMargin_higherMarginRate_returnsHigherMargin() {
        when(currencyPairService.getPairOrDefault("USDZAR")).thenReturn(buildPair("USDZAR", 0.05));

        double margin = calculator.calculateMargin("USDZAR", 1.0, 18.0);

        // Notional = 100,000 × 18.0 = $1,800,000 → margin = $90,000
        assertThat(margin).isCloseTo(90000.0, Offset.offset(0.01));
    }

    // ─── hasEnoughMargin ───────��───────────────────────────��──────────────────

    @Test
    void hasEnoughMargin_sufficientWithBuffer_returnsTrue() {
        // Required=1000, available=2000 → 90% of 2000=1800 > 1000 → true
        assertThat(calculator.hasEnoughMargin(1000.0, 2000.0)).isTrue();
    }

    @Test
    void hasEnoughMargin_exceedsBuffer_returnsFalse() {
        // Required=1900, available=2000 → 90% of 2000=1800 < 1900 → false
        assertThat(calculator.hasEnoughMargin(1900.0, 2000.0)).isFalse();
    }

    @Test
    void hasEnoughMargin_exactlyAtBuffer_returnsTrue() {
        // Required=1800, available=2000 → 90% of 2000=1800 → exactly at limit → true
        assertThat(calculator.hasEnoughMargin(1800.0, 2000.0)).isTrue();
    }

    @Test
    void hasEnoughMargin_zeroRequired_returnsTrue() {
        assertThat(calculator.hasEnoughMargin(0.0, 1000.0)).isTrue();
    }

    // ─── Helper ────────��──────────────────────────────────────────────────────

    private CurrencyPair buildPair(String symbol, double marginRate) {
        CurrencyPair pair = new CurrencyPair();
        pair.setSymbol(symbol);
        pair.setMarginRate(marginRate);
        return pair;
    }
}
