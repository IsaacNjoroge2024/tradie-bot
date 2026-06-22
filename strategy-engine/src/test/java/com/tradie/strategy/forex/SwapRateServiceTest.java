package com.tradie.strategy.forex;

import com.tradie.strategy.forex.dto.SwapRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwapRateServiceTest {

    private SwapRateService swapRateService;

    // 2024-01-01 = Monday, 2024-01-03 = Wednesday, 2024-01-04 = Thursday
    private static final Instant MONDAY    = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2024-01-03T00:00:00Z");
    private static final Instant THURSDAY  = Instant.parse("2024-01-04T00:00:00Z");

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
        // 1 night from Thursday (no Wednesday crossed) → effectiveNights = 1
        // EURUSD long swap = -5.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, THURSDAY);
        assertThat(cost).isEqualTo(-5.0);
    }

    @Test
    void calculateSwapCost_sellPosition_usesShortSwapPoints() {
        // EURUSD short swap = 3.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "SELL", 1.0, 1, THURSDAY);
        assertThat(cost).isEqualTo(3.0);
    }

    @Test
    void calculateSwapCost_multipleNights_multipliesCost() {
        // 3 nights from Thursday (Thu, Fri, Sat) — no Wednesday crossed → pure multiplication
        double oneNight    = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, THURSDAY);
        double threeNights = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 3, THURSDAY);
        assertThat(threeNights).isEqualTo(oneNight * 3);
    }

    @Test
    void calculateSwapCost_halfLot_isHalfOfFullLot() {
        double fullLot = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, THURSDAY);
        double halfLot = swapRateService.calculateSwapCost("EURUSD", "BUY", 0.5, 1, THURSDAY);
        assertThat(halfLot).isEqualTo(fullLot * 0.5);
    }

    @Test
    void calculateSwapCost_caseInsensitiveSide() {
        double upper = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, THURSDAY);
        double lower = swapRateService.calculateSwapCost("EURUSD", "buy", 1.0, 1, THURSDAY);
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void calculateSwapCost_openedOnTripleSwapDay_triplesCharge() {
        // Opened Wednesday, 1 night → tripleSwapDay crossed → effectiveNights = 1 + 2 = 3
        // EURUSD long swap = -5.0 → cost = -5.0 × 1.0 × 3 = -15.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, WEDNESDAY);
        assertThat(cost).isEqualTo(-15.0);
    }

    @Test
    void calculateSwapCost_holdCrossesTripleSwapDay_addsExtraNights() {
        // Opened Monday, 3 nights (Mon, Tue, Wed) → crosses Wednesday once → effectiveNights = 3 + 2 = 5
        // EURUSD long swap = -5.0 → cost = -5.0 × 1.0 × 5 = -25.0
        double cost = swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 3, MONDAY);
        assertThat(cost).isEqualTo(-25.0);
    }

    @Test
    void calculateSwapCost_nullOpenedAt_throwsException() {
        assertThatThrownBy(() -> swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openedAt must not be null");
    }

    @Test
    void calculateSwapCost_invalidSide_throwsException() {
        assertThatThrownBy(() -> swapRateService.calculateSwapCost("EURUSD", "HOLD", 1.0, 1, THURSDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("side must be BUY or SELL");
    }

    @Test
    void calculateSwapCost_negativeLots_throwsException() {
        assertThatThrownBy(() -> swapRateService.calculateSwapCost("EURUSD", "BUY", -1.0, 1, THURSDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lots and nights must be non-negative");
    }

    @Test
    void calculateSwapCost_negativeNights_throwsException() {
        assertThatThrownBy(() -> swapRateService.calculateSwapCost("EURUSD", "BUY", 1.0, -1, THURSDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lots and nights must be non-negative");
    }

    // ─── countTripleSwapNights ────────────────────────────────────────────────

    @Test
    void countTripleSwapNights_singleNightOnTripleSwapDay_returnsOne() {
        assertThat(swapRateService.countTripleSwapNights(WEDNESDAY, 1, DayOfWeek.WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void countTripleSwapNights_singleNightNotOnTripleSwapDay_returnsZero() {
        assertThat(swapRateService.countTripleSwapNights(THURSDAY, 1, DayOfWeek.WEDNESDAY)).isEqualTo(0);
    }

    @Test
    void countTripleSwapNights_threeNightsFromMonday_returnsOne() {
        // Mon(i=0), Tue(i=1), Wed(i=2) → 1 crossing
        assertThat(swapRateService.countTripleSwapNights(MONDAY, 3, DayOfWeek.WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void countTripleSwapNights_sevenNightsFromMonday_returnsOne() {
        // Mon..Sun → 1 Wednesday
        assertThat(swapRateService.countTripleSwapNights(MONDAY, 7, DayOfWeek.WEDNESDAY)).isEqualTo(1);
    }

    @Test
    void countTripleSwapNights_fourteenNightsFromMonday_returnsTwo() {
        // Two full weeks → 2 Wednesdays
        assertThat(swapRateService.countTripleSwapNights(MONDAY, 14, DayOfWeek.WEDNESDAY)).isEqualTo(2);
    }

    @Test
    void countTripleSwapNights_zeroNights_returnsZero() {
        assertThat(swapRateService.countTripleSwapNights(WEDNESDAY, 0, DayOfWeek.WEDNESDAY)).isEqualTo(0);
    }
}
