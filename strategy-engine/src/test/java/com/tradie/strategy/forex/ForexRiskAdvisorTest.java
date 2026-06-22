package com.tradie.strategy.forex;

import com.tradie.strategy.config.ForexProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexRiskAdvisorTest {

    @Mock
    private ForexSessionService sessionService;

    private ForexProperties forexProperties;
    private ForexRiskAdvisor advisor;

    // 2024-01-05 = Friday, 2024-01-06 = Saturday, 2024-01-07 = Sunday, 2024-01-08 = Monday
    private static final Instant FRIDAY_AFTER_18   = Instant.parse("2024-01-05T20:00:00Z");
    private static final Instant FRIDAY_BEFORE_18  = Instant.parse("2024-01-05T10:00:00Z");
    private static final Instant SATURDAY_MORNING  = Instant.parse("2024-01-06T10:00:00Z");
    private static final Instant SUNDAY_MORNING    = Instant.parse("2024-01-07T10:00:00Z");
    private static final Instant MONDAY_MORNING    = Instant.parse("2024-01-08T10:00:00Z");
    private static final Instant WEDNESDAY_OVERLAP = Instant.parse("2024-01-03T15:00:00Z");

    @BeforeEach
    void setUp() {
        forexProperties = new ForexProperties();
        advisor = new ForexRiskAdvisor(sessionService, forexProperties);
    }

    // ─── isWeekendApproaching ─────────────────────────────────────────────────

    @Test
    void isWeekendApproaching_fridayAfter18_returnsTrue() {
        assertThat(advisor.isWeekendApproaching(FRIDAY_AFTER_18)).isTrue();
    }

    @Test
    void isWeekendApproaching_fridayBefore18_returnsFalse() {
        assertThat(advisor.isWeekendApproaching(FRIDAY_BEFORE_18)).isFalse();
    }

    @Test
    void isWeekendApproaching_saturday_returnsTrue() {
        assertThat(advisor.isWeekendApproaching(SATURDAY_MORNING)).isTrue();
    }

    @Test
    void isWeekendApproaching_sunday_returnsTrue() {
        assertThat(advisor.isWeekendApproaching(SUNDAY_MORNING)).isTrue();
    }

    @Test
    void isWeekendApproaching_monday_returnsFalse() {
        assertThat(advisor.isWeekendApproaching(MONDAY_MORNING)).isFalse();
    }

    // ─── getWarnings ──────────────────────────────────────────────────────────

    @Test
    void getWarnings_weekendApproaching_addsWeekendWarning() {
        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, FRIDAY_AFTER_18, false);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("Weekend gap risk");
    }

    @Test
    void getWarnings_weekendWarningDisabled_noWeekendWarning() {
        forexProperties.getRisk().setWeekendWarning(false);

        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, FRIDAY_AFTER_18, false);

        assertThat(warnings).isEmpty();
    }

    @Test
    void getWarnings_highSpread_addsSpreadWarning() {
        // Default maxSpreadPips = 5.0; sending 8.0 should trigger warning
        List<String> warnings = advisor.getWarnings("EURUSD", 8.0, MONDAY_MORNING, false);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("High spread detected");
        assertThat(warnings.get(0)).contains("8.0");
    }

    @Test
    void getWarnings_spreadBelowMax_noSpreadWarning() {
        List<String> warnings = advisor.getWarnings("EURUSD", 3.0, MONDAY_MORNING, false);

        assertThat(warnings).isEmpty();
    }

    @Test
    void getWarnings_lowLiquidityNewPosition_addsLowLiquidityWarning() {
        when(sessionService.isLowLiquidity(MONDAY_MORNING)).thenReturn(true);
        when(sessionService.isHighLiquidityPeriod(MONDAY_MORNING)).thenReturn(false);

        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, MONDAY_MORNING, true);

        assertThat(warnings).anyMatch(w -> w.contains("Low liquidity"));
    }

    @Test
    void getWarnings_lowLiquidityExistingPosition_noLowLiquidityWarning() {
        // isNewPosition=false → low-liquidity check is skipped
        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, MONDAY_MORNING, false);

        assertThat(warnings).noneMatch(w -> w.contains("Low liquidity"));
    }

    @Test
    void getWarnings_outsideOverlapNewPosition_addsOverlapWarning() {
        when(sessionService.isLowLiquidity(MONDAY_MORNING)).thenReturn(false);
        when(sessionService.isHighLiquidityPeriod(MONDAY_MORNING)).thenReturn(false);

        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, MONDAY_MORNING, true);

        assertThat(warnings).anyMatch(w -> w.contains("Outside London-NY overlap"));
    }

    @Test
    void getWarnings_duringLondonNyOverlap_noOverlapWarning() {
        when(sessionService.isLowLiquidity(WEDNESDAY_OVERLAP)).thenReturn(false);
        when(sessionService.isHighLiquidityPeriod(WEDNESDAY_OVERLAP)).thenReturn(true);

        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, WEDNESDAY_OVERLAP, true);

        assertThat(warnings).noneMatch(w -> w.contains("Outside London-NY overlap"));
    }

    @Test
    void getWarnings_goodConditions_noWarnings() {
        when(sessionService.isLowLiquidity(WEDNESDAY_OVERLAP)).thenReturn(false);
        when(sessionService.isHighLiquidityPeriod(WEDNESDAY_OVERLAP)).thenReturn(true);

        List<String> warnings = advisor.getWarnings("EURUSD", 2.0, WEDNESDAY_OVERLAP, true);

        assertThat(warnings).isEmpty();
    }
}
