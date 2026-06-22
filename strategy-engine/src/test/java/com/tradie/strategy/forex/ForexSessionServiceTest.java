package com.tradie.strategy.forex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForexSessionServiceTest {

    private ForexSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new ForexSessionService();
    }

    private Instant atUtcHour(int hour) {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .withHour(hour).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }

    // ─── getActiveSessions ────────────────────────────────────────────────────

    @Test
    void getActiveSessions_londonMidmorning_includesLondon() {
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(10));
        assertThat(sessions).contains(ForexSessionService.ForexSession.LONDON);
    }

    @Test
    void getActiveSessions_newYorkAfternoon_includesNewYork() {
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(15));
        assertThat(sessions).contains(ForexSessionService.ForexSession.NEW_YORK);
    }

    @Test
    void getActiveSessions_overlapPeriod_includesBothLondonAndNY() {
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(14));
        assertThat(sessions).contains(ForexSessionService.ForexSession.LONDON);
        assertThat(sessions).contains(ForexSessionService.ForexSession.NEW_YORK);
    }

    @Test
    void getActiveSessions_tokyoEarlyMorning_includesTokyo() {
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(3));
        assertThat(sessions).contains(ForexSessionService.ForexSession.TOKYO);
    }

    @Test
    void getActiveSessions_sydneyNight_includesSydney() {
        // 23:00 UTC is within Sydney session (22:00–07:00)
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(23));
        assertThat(sessions).contains(ForexSessionService.ForexSession.SYDNEY);
    }

    @Test
    void getActiveSessions_sydneyEarlyMorning_includesSydney() {
        // 05:00 UTC is within Sydney session (wraps midnight)
        List<ForexSessionService.ForexSession> sessions = sessionService.getActiveSessions(atUtcHour(5));
        assertThat(sessions).contains(ForexSessionService.ForexSession.SYDNEY);
    }

    // ─── isHighLiquidityPeriod ────────────────────────────────────────────────

    @Test
    void isHighLiquidityPeriod_duringOverlap_returnsTrue() {
        assertThat(sessionService.isHighLiquidityPeriod(atUtcHour(14))).isTrue();
    }

    @Test
    void isHighLiquidityPeriod_atOverlapStart_returnsTrue() {
        assertThat(sessionService.isHighLiquidityPeriod(atUtcHour(13))).isTrue();
    }

    @Test
    void isHighLiquidityPeriod_atOverlapEnd_returnsFalse() {
        // 17:00 is just after the overlap ends
        assertThat(sessionService.isHighLiquidityPeriod(atUtcHour(17))).isFalse();
    }

    @Test
    void isHighLiquidityPeriod_londonOnlyHour_returnsFalse() {
        assertThat(sessionService.isHighLiquidityPeriod(atUtcHour(10))).isFalse();
    }

    // ─── isLowLiquidity ───────────────────────────────────────────────────────

    @Test
    void isLowLiquidity_duringLondonNyOverlap_returnsFalse() {
        // 14:00 UTC: London + NY both active
        assertThat(sessionService.isLowLiquidity(atUtcHour(14))).isFalse();
    }

    @Test
    void isLowLiquidity_tokyoAndSydneyOnly_returnsTrue() {
        // 03:00 UTC: Tokyo + Sydney active, but neither London nor NY → low liquidity
        assertThat(sessionService.isLowLiquidity(atUtcHour(3))).isTrue();
    }
}
