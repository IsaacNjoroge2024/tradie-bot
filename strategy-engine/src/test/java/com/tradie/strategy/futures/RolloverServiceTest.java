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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolloverServiceTest {

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
        service = new RolloverService(repository, futuresProperties, orderPublisher);
    }

    // ─── checkForUpcomingRollovers ────────────────────────────────────────────

    @Test
    void checkForUpcomingRollovers_contractDueToRoll_returnsAlert() {
        // Expiration is 5 days away → rollDate = expiration - 8 = 3 days ago → daysToRoll = -3
        // So let's set expiration 12 days away → rollDate = 12 - 8 = 4 days from now → within 14
        FuturesContract current = buildContract("ESM5", "ES", LocalDate.now().plusDays(12));
        FuturesContract next    = buildContract("ESU5", "ES", LocalDate.now().plusDays(105));
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(current, next));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(1);
        RolloverAlert alert = alerts.get(0);
        assertThat(alert.symbol()).isEqualTo("ES");
        assertThat(alert.currentContract()).isEqualTo("ESM5");
        assertThat(alert.nextContract()).isEqualTo("ESU5");
        assertThat(alert.daysRemaining()).isEqualTo(4); // rollDate is 4 days from now
    }

    @Test
    void checkForUpcomingRollovers_contractFarAway_noAlert() {
        FuturesContract contract = buildContract("ESM5", "ES", LocalDate.now().plusDays(60));
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
        FuturesContract current = buildContract("ESM5", "ES", LocalDate.now().plusDays(12));
        when(repository.findByActiveTrue()).thenReturn(List.of(current));
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(current));

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).nextContract()).isEqualTo("UNKNOWN");
    }

    @Test
    void checkForUpcomingRollovers_sortedByDaysRemaining() {
        FuturesContract cl = buildContract("CLN5", "CL", LocalDate.now().plusDays(9));  // roll in 1 day
        FuturesContract es = buildContract("ESM5", "ES", LocalDate.now().plusDays(12)); // roll in 4 days
        when(repository.findByActiveTrue()).thenReturn(List.of(es, cl));
        when(repository.findBySymbolAndActiveTrue(anyString())).thenReturn(List.of());

        List<RolloverAlert> alerts = service.checkForUpcomingRollovers(14);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).currentContract()).isEqualTo("CLN5");
        assertThat(alerts.get(1).currentContract()).isEqualTo("ESM5");
    }

    // ─── shouldRoll ───────────────────────────────────────────────────────────

    @Test
    void shouldRoll_pastRollDate_returnsTrue() {
        // Expiration 5 days away → rollDate = today - 3 → already past
        FuturesContract contract = buildContract("ESM5", "ES", LocalDate.now().plusDays(5));
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.shouldRoll("ESM5")).isTrue();
    }

    @Test
    void shouldRoll_beforeRollDate_returnsFalse() {
        // Expiration 30 days away → rollDate = today + 22 → not yet
        FuturesContract contract = buildContract("ESM5", "ES", LocalDate.now().plusDays(30));
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

    // ─── publishRolloverAlerts ────────────────────────────────────────────────

    @Test
    void publishRolloverAlerts_contractDueToRoll_publishesAlert() {
        FuturesContract current = buildContract("ESM5", "ES", LocalDate.now().plusDays(12));
        FuturesContract next    = buildContract("ESU5", "ES", LocalDate.now().plusDays(105));
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
                buildContract("ESM5", "ES", LocalDate.now().plusDays(60))
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
