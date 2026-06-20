package com.tradie.strategy.forex;

import com.tradie.strategy.forex.dto.SwapRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwapRateServiceTest {

    private SwapRateService swapRateService;

    @BeforeEach
    void setUp() {
        swapRateService = new SwapRateService();
    }

    // ─── getSwapRate ──────────────────────────────────────────────────────────

    @Test
    void getSwapRate_knownPair_returnsRate() {
        SwapRate rate = swapRateService.getSwapRate("EURUSD");
        assertThat(rate).isNotNull();
        assertThat(rate.pair()).isEqualTo("EURUSD");
    }

    @Test
    void getSwapRate_slashFormat_normalizedAndFound() {
        SwapRate rate = swapRateService.getSwapRate("EUR/USD");
        assertThat(rate.pair()).isEqualTo("EURUSD");
    }

    @Test
    void getSwapRate_unknownPair_returnsDefault() {
        SwapRate rate = swapRateService.getSwapRate("XYZABC");
        assertThat(rate).isNotNull();
        assertThat(rate.pair()).isEqualTo("UNKNOWN");
    }

    // ─── calculateSwapCost ────────────────────────────────────────────────────

    @Test
    void calculateSwapCost_buyPosition_usesLongSwapPoints() {
        // EURUSD long swap = -5.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1);
        assertThat(cost).isEqualTo(-5.0);
    }

    @Test
    void calculateSwapCost_sellPosition_usesShortSwapPoints() {
        // EURUSD short swap = 3.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "SELL", 1.0, 1);
        assertThat(cost).isEqualTo(3.0);
    }

    @Test
    void calculateSwapCost_multipleNights_multipliesCost() {
        double oneNight    = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1);
        double threeNights = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 3);
        assertThat(threeNights).isEqualTo(oneNight * 3);
    }

    @Test
    void calculateSwapCost_halfLot_isHalfOfFullLot() {
        double fullLot = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1);
        double halfLot = swapRateService.calculateSwapCost("EURUSD", "BUY", 0.5, 1);
        assertThat(halfLot).isEqualTo(fullLot * 0.5);
    }

    @Test
    void calculateSwapCost_caseInsensitiveSide() {
        double upper = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1);
        double lower = swapRateService.calculateSwapCost("EURUSD", "buy", 1.0, 1);
        assertThat(lower).isEqualTo(upper);
    }

    // ─── calculateEffectiveNights ─────────────────────────────────────────────

    @Test
    void calculateEffectiveNights_lessThanOneWeek_noTripleSwap() {
        assertThat(swapRateService.calculateEffectiveNights(1)).isEqualTo(1);
        assertThat(swapRateService.calculateEffectiveNights(6)).isEqualTo(6);
    }

    @Test
    void calculateEffectiveNights_exactlyOneWeek_addsTripleSwap() {
        // 7 nights = 1 full week → +2 extra nights for Wednesday triple swap
        assertThat(swapRateService.calculateEffectiveNights(7)).isEqualTo(9);
    }

    @Test
    void calculateEffectiveNights_twoWeeks_addsTwoTripleSwaps() {
        assertThat(swapRateService.calculateEffectiveNights(14)).isEqualTo(18);
    }
}
