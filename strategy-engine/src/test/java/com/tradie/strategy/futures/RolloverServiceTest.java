package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import com.tradie.strategy.config.FuturesProperties;
import com.tradie.strategy.futures.dto.RolloverAlert;
import com.tradie.strategy.service.OrderPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Fixed clock: Monday 2026-06-22 UTC.
 *
 * Business-day rollover dates (8 BDs before expiration):
 *  expiry 2026-07-03 (Fri)  → rollDate 2026-06-23 (Tue) → daysRemaining 1
 *  expiry 2026-07-07 (Mon)  → rollDate 2026-06-25 (Wed) → daysRemaining 3
 *  expiry 2026-06-27 (Sat)  → rollDate 2026-06-17 (past)→ shouldRoll = true
 *  expiry 2026-07-22 (Wed)  → rollDate 2026-07-10 (future) → shouldRoll = false
 *  expiry 2026-09-30 (Wed)  → rollDate far future → no alert
 */
@ExtendWith(MockitoExtension.class)
class RolloverServiceTest {

    // Fixed "today" = Monday 2026-06-22 00:00:00 UTC
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC);

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    // Expiry dates chosen so business-day roll dates are predictable
    /** expiry 2026-07-07 (Mon) → rollDate 2026-06-25 (Wed) → daysRemaining 3 */
    private static final LocalDate EXPIRY_ROLL_IN_3 = LocalDate.of(2026, 7, 7);
    /** expiry 2026-07-03 (Fri) → rollDate 2026-06-23 (Tue) → daysRemaining 1 */
    private static final LocalDate EXPIRY_ROLL_IN_1 = LocalDate.of(2026, 7, 3);
    /** expiry 2026-09-30 — rollDate far in the future */
    private static final LocalDate EXPIRY_FAR = LocalDate.of(2026, 9, 30);
    /** expiry 2026-06-27 (Sat) → rollDate 2026-06-17 → already past */
    private static final LocalDate EXPIRY_PAST_ROLL = LocalDate.of(2026, 6, 27);
    /** expiry 2026-07-22 (Wed) → rollDate 2026-07-10 → not yet */
    private static final LocalDate EXPIRY_FUTURE_ROLL = LocalDate.of(2026, 7, 22);

    @Mock
    private FuturesContractRepository repository;

    @Mock
    private OrderPublisher orderPublisher;

    private FuturesProperties futuresProperties;
    private RolloverService service;

    @BeforeEach
    void setUp() {
        futuresProperties = new FuturesProperties();
        futuresProperties.setDefaultRolloverDays(8);
        service = new RolloverService(repository, futuresProperties, orderPublisher, FIXED_CLOCK);
    }

    // ─── checkForUpcomingRollovers ────────────────────────────────────────────

    @Test
    void checkForUpcomingRollovers_contractDueToRoll_returnsAlert() {
        FuturesContract current = buildContract("ESM5", "ES", EXPIRY_ROLL_IN_3);
        FuturesContract next    = buildContract("ESU5", "ES", EXPIRY_FAR);
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(current, next));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(1);
        RolloverAlert alert = alerts.get(0);
        assertThat(alert.symbol()).isEqualTo("ES");
        assertThat(alert.currentContract()).isEqualTo("ESM5");
        assertThat(alert.nextContract()).isEqualTo("ESU5");
        assertThat(alert.daysRemaining()).isEqualTo(3); // rollDate 2026-06-25 is 3 days from today
    }

    @Test
    void checkForUpcomingRollovers_contractFarAway_noAlert() {
        FuturesContract contract = buildContract("ESM5", "ES", EXPIRY_FAR);
        when(repository.findByActiveTrue()).thenReturn(List.of(contract));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).isEmpty();
    }

    @Test
    void checkForUpcomingRollovers_noExpirationDate_skipped() {
        FuturesContract contract = new FuturesContract();
        contract.setFullSymbol("ESM5");
        contract.setSymbol("ES");
        contract.setExpirationDate(null);
        when(repository.findByActiveTrue()).thenReturn(List.of(contract));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).isEmpty();
    }

    @Test
    void checkForUpcomingRollovers_noNextContract_alertShowsUnknown() {
        FuturesContract current = buildContract("ESM5", "ES", EXPIRY_ROLL_IN_3);
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(current));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).nextContract()).isEqualTo("UNKNOWN");
    }

    @Test
    void checkForUpcomingRollovers_sortedByDaysRemaining() {
        // CL rolls in 1 day (rollDate June 23), ES rolls in 3 days (rollDate June 25)
        FuturesContract cl = buildContract("CLN5", "CL", EXPIRY_ROLL_IN_1);
        FuturesContract es = buildContract("ESM5", "ES", EXPIRY_ROLL_IN_3);
        when(repository.findByActiveTrue()).thenReturn(List.of(es, cl));
        when(repository.findBySymbolAndActiveTrue(anyString())).thenReturn(List.of());

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).currentContract()).isEqualTo("CLN5"); // 1 day
        assertThat(alerts.get(1).currentContract()).isEqualTo("ESM5"); // 3 days
    }

    // ─── shouldRoll ───────────────────────────────────────────────────────────

    @Test
    void shouldRoll_pastRollDate_returnsTrue() {
        // expiry 2026-06-27 (Sat) → 8 BDs back → rollDate 2026-06-17 (past today 2026-06-22)
        FuturesContract contract = buildContract("ESM5", "ES", EXPIRY_PAST_ROLL);
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.shouldRoll("ESM5")).isTrue();
    }

    @Test
    void shouldRoll_beforeRollDate_returnsFalse() {
        // expiry 2026-07-22 (Wed) → 8 BDs back → rollDate 2026-07-10 (future from today 2026-06-22)
        FuturesContract contract = buildContract("ESM5", "ES", EXPIRY_FUTURE_ROLL);
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.shouldRoll("ESM5")).isFalse();
    }

    @Test
    void shouldRoll_contractNotFound_returnsFalse() {
        when(repository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.shouldRoll("UNKNOWN")).isFalse();
    }

    @Test
    void shouldRoll_nullExpirationDate_returnsFalse() {
        FuturesContract contract = new FuturesContract();
        contract.setFullSymbol("ESM5");
        contract.setExpirationDate(null);
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.shouldRoll("ESM5")).isFalse();
    }

    // ─── shouldRollByRootSymbol ───────────────────────────────────────────────

    @Test
    void shouldRollByRootSymbol_frontMonthPastRollDate_returnsTrue() {
        FuturesContract contract = buildContract("ESM5", "ES", EXPIRY_PAST_ROLL);
        when(repository.findBySymbolAndActiveTrueAndFrontMonthTrue("ES")).thenReturn(Optional.of(contract));
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.shouldRollByRootSymbol("ES")).isTrue();
    }

    @Test
    void shouldRollByRootSymbol_noFrontMonthInDb_returnsFalse() {
        when(repository.findBySymbolAndActiveTrueAndFrontMonthTrue("ES")).thenReturn(Optional.empty());

        assertThat(service.shouldRollByRootSymbol("ES")).isFalse();
    }

    // ─── publishRolloverAlerts ────────────────────────────────────────────────

    @Test
    void publishRolloverAlerts_contractDueToRoll_publishesAlert() {
        FuturesContract current = buildContract("ESM5", "ES", EXPIRY_ROLL_IN_3);
        FuturesContract next    = buildContract("ESU5", "ES", EXPIRY_FAR);
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(current, next));

        service.publishRolloverAlerts();

        verify(orderPublisher).publishSystemAlert(
                eq("RolloverService"),
                eq("ROLLOVER_ALERT"),
                contains("ESM5"));
    }

    @Test
    void publishRolloverAlerts_noUpcomingRollovers_noPublish() {
        when(repository.findByActiveTrue()).thenReturn(List.of(
                buildContract("ESM5", "ES", EXPIRY_FAR)
        ));

        service.publishRolloverAlerts();

        verifyNoInteractions(orderPublisher);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private FuturesContract buildContract(String fullSymbol, String symbol, LocalDate expirationDate) {
        FuturesContract c = new FuturesContract();
        c.setFullSymbol(fullSymbol);
        c.setSymbol(symbol);
        c.setExpirationDate(expirationDate);
        c.setActive(true);
        return c;
    }
}
