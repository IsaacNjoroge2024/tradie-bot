package com.tradie.strategy.futures;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FuturesMonthCodeTest {

    // ─── buildContractSymbol ──────────────────────────────────────────────────

    @Test
    void buildContractSymbol_esMarch2025_returnsESH5() {
        assertThat(FuturesMonthCode.buildContractSymbol("ES", 3, 2025)).isEqualTo("ESH5");
    }

    @Test
    void buildContractSymbol_nqJune2025_returnsNQM5() {
        assertThat(FuturesMonthCode.buildContractSymbol("NQ", 6, 2025)).isEqualTo("NQM5");
    }

    @Test
    void buildContractSymbol_clJuly2025_returnsCLN5() {
        assertThat(FuturesMonthCode.buildContractSymbol("CL", 7, 2025)).isEqualTo("CLN5");
    }

    @Test
    void buildContractSymbol_gcAugust2025_returnsGCQ5() {
        assertThat(FuturesMonthCode.buildContractSymbol("GC", 8, 2025)).isEqualTo("GCQ5");
    }

    @Test
    void buildContractSymbol_december2030_usesLastYearDigit() {
        // 2030 % 10 = 0
        assertThat(FuturesMonthCode.buildContractSymbol("ES", 12, 2030)).isEqualTo("ESZ0");
    }

    @Test
    void buildContractSymbol_invalidMonth_throwsException() {
        assertThatThrownBy(() -> FuturesMonthCode.buildContractSymbol("ES", 13, 2025))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Month must be 1-12");
    }

    @Test
    void buildContractSymbol_zeroMonth_throwsException() {
        assertThatThrownBy(() -> FuturesMonthCode.buildContractSymbol("ES", 0, 2025))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── getMonthFromCode ─────────────────────────────────────────────────────

    @Test
    void getMonthFromCode_H_returnsMarch() {
        assertThat(FuturesMonthCode.getMonthFromCode('H')).isEqualTo(3);
    }

    @Test
    void getMonthFromCode_lowercase_recognizedAfterUpperCase() {
        assertThat(FuturesMonthCode.getMonthFromCode('h')).isEqualTo(3);
    }

    @Test
    void getMonthFromCode_Z_returnsDecember() {
        assertThat(FuturesMonthCode.getMonthFromCode('Z')).isEqualTo(12);
    }

    @Test
    void getMonthFromCode_invalid_throwsException() {
        assertThatThrownBy(() -> FuturesMonthCode.getMonthFromCode('A'))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid futures month code");
    }

    // ─── toLocalSymbolSuffix ──────────────────────────────────────────────────

    @Test
    void toLocalSymbolSuffix_202503_returnsH5() {
        assertThat(FuturesMonthCode.toLocalSymbolSuffix("202503")).isEqualTo("H5");
    }

    @Test
    void toLocalSymbolSuffix_202506_returnsM5() {
        assertThat(FuturesMonthCode.toLocalSymbolSuffix("202506")).isEqualTo("M5");
    }

    @Test
    void toLocalSymbolSuffix_202512_returnsZ5() {
        assertThat(FuturesMonthCode.toLocalSymbolSuffix("202512")).isEqualTo("Z5");
    }

    @Test
    void toLocalSymbolSuffix_malformedInput_throwsException() {
        assertThatThrownBy(() -> FuturesMonthCode.toLocalSymbolSuffix("2025"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYYMM format");
    }

    @Test
    void toLocalSymbolSuffix_null_throwsException() {
        assertThatThrownBy(() -> FuturesMonthCode.toLocalSymbolSuffix(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── round-trip ───────────────────────────────────────────────────────────

    @Test
    void monthCodes_allTwelveMonthsMapped() {
        assertThat(FuturesMonthCode.MONTH_CODES).hasSize(12);
        for (int m = 1; m <= 12; m++) {
            assertThat(FuturesMonthCode.MONTH_CODES).containsKey(m);
        }
    }
}
