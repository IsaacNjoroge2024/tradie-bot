package com.tradie.strategy.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class CryptoPropertiesTest {

    private final CryptoProperties.Risk risk = new CryptoProperties.Risk();

    // ─── maxPositionPct ───────────────────────────────────────────────────────

    @Test
    void setMaxPositionPct_zero_throws() {
        assertThatThrownBy(() -> risk.setMaxPositionPct(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPositionPct");
    }

    @Test
    void setMaxPositionPct_negative_throws() {
        assertThatThrownBy(() -> risk.setMaxPositionPct(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setMaxPositionPct_over100_throws() {
        assertThatThrownBy(() -> risk.setMaxPositionPct(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPositionPct");
    }

    @Test
    void setMaxPositionPct_exactly100_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMaxPositionPct(100));
    }

    @Test
    void setMaxPositionPct_typical_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMaxPositionPct(5));
    }

    // ─── maxTotalExposurePct ──────────────────────────────────────────────────

    @Test
    void setMaxTotalExposurePct_zero_throws() {
        assertThatThrownBy(() -> risk.setMaxTotalExposurePct(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotalExposurePct");
    }

    @Test
    void setMaxTotalExposurePct_over100_throws() {
        assertThatThrownBy(() -> risk.setMaxTotalExposurePct(150))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotalExposurePct");
    }

    @Test
    void setMaxTotalExposurePct_exactly100_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMaxTotalExposurePct(100));
    }

    @Test
    void setMaxTotalExposurePct_typical_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMaxTotalExposurePct(15));
    }

    // ─── minStopLossPct ───────────────────────────────────────────────────────

    @Test
    void setMinStopLossPct_negative_throws() {
        assertThatThrownBy(() -> risk.setMinStopLossPct(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minStopLossPct");
    }

    @Test
    void setMinStopLossPct_over100_throws() {
        assertThatThrownBy(() -> risk.setMinStopLossPct(101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minStopLossPct");
    }

    @Test
    void setMinStopLossPct_zero_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMinStopLossPct(0));
    }

    @Test
    void setMinStopLossPct_exactly100_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMinStopLossPct(100));
    }

    @Test
    void setMinStopLossPct_typical_valid() {
        assertThatNoException().isThrownBy(() -> risk.setMinStopLossPct(3));
    }
}
