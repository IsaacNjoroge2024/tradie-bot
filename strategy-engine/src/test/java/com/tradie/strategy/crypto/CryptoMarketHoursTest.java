package com.tradie.strategy.crypto;

import com.tradie.strategy.config.CryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoMarketHoursTest {

    private CryptoProperties properties;
    private CryptoMarketHours marketHours;

    @BeforeEach
    void setUp() {
        properties = new CryptoProperties();
        marketHours = new CryptoMarketHours(properties);
    }

    @Test
    void isMarketOpen_alwaysReturnsTrue() {
        assertThat(marketHours.isMarketOpen()).isTrue();
    }

    // ─── shouldTrade — skip-low-volume-hours disabled (default) ──────────────

    @Test
    void shouldTrade_skipDisabled_alwaysAllows() {
        properties.setSkipLowVolumeHours(false);

        // Even during low-volume hours
        Instant lowVolume = utcInstant(3, 0);
        assertThat(marketHours.shouldTrade(lowVolume)).isTrue();
    }

    // ─── shouldTrade — skip-low-volume-hours enabled ─────────────────────────

    @Test
    void shouldTrade_skipEnabled_allowsAfterSixUtc() {
        properties.setSkipLowVolumeHours(true);

        Instant activeHour = utcInstant(10, 0);
        assertThat(marketHours.shouldTrade(activeHour)).isTrue();
    }

    @Test
    void shouldTrade_skipEnabled_blocksMidnightToSix() {
        properties.setSkipLowVolumeHours(true);

        assertThat(marketHours.shouldTrade(utcInstant(0, 0))).isFalse();
        assertThat(marketHours.shouldTrade(utcInstant(1, 30))).isFalse();
        assertThat(marketHours.shouldTrade(utcInstant(3, 0))).isFalse();
        assertThat(marketHours.shouldTrade(utcInstant(5, 59))).isFalse();
    }

    @Test
    void shouldTrade_skipEnabled_allowsExactlySixUtc() {
        properties.setSkipLowVolumeHours(true);

        assertThat(marketHours.shouldTrade(utcInstant(6, 0))).isTrue();
    }

    @Test
    void shouldTrade_skipEnabled_blocksBefore6AfterMidnight() {
        properties.setSkipLowVolumeHours(true);

        assertThat(marketHours.shouldTrade(utcInstant(5, 0))).isFalse();
    }

    @Test
    void shouldTrade_skipEnabled_allowsLateEvening() {
        properties.setSkipLowVolumeHours(true);

        assertThat(marketHours.shouldTrade(utcInstant(23, 0))).isTrue();
        assertThat(marketHours.shouldTrade(utcInstant(20, 30))).isTrue();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Instant utcInstant(int hour, int minute) {
        return ZonedDateTime.of(2024, 6, 15, hour, minute, 0, 0, ZoneOffset.UTC).toInstant();
    }
}
