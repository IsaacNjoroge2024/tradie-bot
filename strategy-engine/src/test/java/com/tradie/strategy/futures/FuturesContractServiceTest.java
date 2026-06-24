package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import com.tradie.strategy.futures.dto.FuturesSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuturesContractServiceTest {

    @Mock
    private FuturesContractRepository repository;

    private FuturesContractService service;

    @BeforeEach
    void setUp() {
        service = new FuturesContractService(repository);
    }

    // ─── getContractSpec ──────────────────────────────────────────────────────

    @Test
    void getContractSpec_found_returnsSpec() {
        when(repository.findById("ESM5")).thenReturn(Optional.of(buildContract("ESM5", "ES", "202506")));

        Optional<FuturesSpec> result = service.getContractSpec("ESM5");

        assertThat(result).isPresent();
        assertThat(result.get().fullSymbol()).isEqualTo("ESM5");
        assertThat(result.get().symbol()).isEqualTo("ES");
        assertThat(result.get().contractMonth()).isEqualTo("202506");
        assertThat(result.get().multiplier()).isEqualTo(50.0);
        assertThat(result.get().tickSize()).isEqualTo(0.25);
        assertThat(result.get().tickValue()).isEqualTo(12.50);
        assertThat(result.get().exchange()).isEqualTo("CME");
    }

    @Test
    void getContractSpec_notFound_returnsEmpty() {
        when(repository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.getContractSpec("UNKNOWN")).isEmpty();
    }

    // ─── getFrontMonthContract ────────────────────────────────────────────────

    @Test
    void getFrontMonthContract_found_returnsSpec() {
        when(repository.findBySymbolAndActiveTrueAndFrontMonthTrue("ES"))
                .thenReturn(Optional.of(buildContract("ESM5", "ES", "202506")));

        Optional<FuturesSpec> result = service.getFrontMonthContract("ES");

        assertThat(result).isPresent();
        assertThat(result.get().fullSymbol()).isEqualTo("ESM5");
    }

    @Test
    void getFrontMonthContract_notFound_returnsEmpty() {
        when(repository.findBySymbolAndActiveTrueAndFrontMonthTrue("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.getFrontMonthContract("UNKNOWN")).isEmpty();
    }

    // ─── getActiveContracts ───────────────────────────────────────────────────

    @Test
    void getActiveContracts_returnsAllActiveForSymbol() {
        FuturesContract esm5 = buildContract("ESM5", "ES", "202506");
        FuturesContract esu5 = buildContract("ESU5", "ES", "202509");
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of(esm5, esu5));

        List<FuturesSpec> result = service.getActiveContracts("ES");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FuturesSpec::fullSymbol)
                .containsExactlyInAnyOrder("ESM5", "ESU5");
    }

    @Test
    void getActiveContracts_noContracts_returnsEmpty() {
        when(repository.findBySymbolAndActiveTrue("XY")).thenReturn(List.of());

        assertThat(service.getActiveContracts("XY")).isEmpty();
    }

    // ─── isNearExpiration ─────────────────────────────────────────────────────

    @Test
    void isNearExpiration_expiresInFiveDays_withinThreshold_returnsTrue() {
        FuturesContract contract = buildContract("ESM5", "ES", "202506");
        contract.setExpirationDate(LocalDate.now().plusDays(5));
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.isNearExpiration("ESM5", 8)).isTrue();
    }

    @Test
    void isNearExpiration_expiresInTenDays_outsideThreshold_returnsFalse() {
        FuturesContract contract = buildContract("ESM5", "ES", "202506");
        contract.setExpirationDate(LocalDate.now().plusDays(10));
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.isNearExpiration("ESM5", 8)).isFalse();
    }

    @Test
    void isNearExpiration_alreadyExpired_returnsFalse() {
        FuturesContract contract = buildContract("ESH5", "ES", "202503");
        contract.setExpirationDate(LocalDate.now().minusDays(1));
        when(repository.findById("ESH5")).thenReturn(Optional.of(contract));

        assertThat(service.isNearExpiration("ESH5", 8)).isFalse();
    }

    @Test
    void isNearExpiration_notFound_returnsFalse() {
        when(repository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThat(service.isNearExpiration("UNKNOWN", 8)).isFalse();
    }

    @Test
    void isNearExpiration_expiresExactlyAtThreshold_returnsTrue() {
        FuturesContract contract = buildContract("ESM5", "ES", "202506");
        contract.setExpirationDate(LocalDate.now().plusDays(8));
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.isNearExpiration("ESM5", 8)).isTrue();
    }

    @Test
    void isNearExpiration_nullExpirationDate_returnsFalse() {
        FuturesContract contract = buildContract("ESM5", "ES", "202506");
        contract.setExpirationDate(null);
        when(repository.findById("ESM5")).thenReturn(Optional.of(contract));

        assertThat(service.isNearExpiration("ESM5", 8)).isFalse();
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private FuturesContract buildContract(String fullSymbol, String symbol, String contractMonth) {
        FuturesContract c = new FuturesContract();
        c.setFullSymbol(fullSymbol);
        c.setSymbol(symbol);
        c.setContractMonth(contractMonth);
        c.setExchange("CME");
        c.setMultiplier(50.0);
        c.setTickSize(0.25);
        c.setTickValue(12.50);
        c.setInitialMargin(12000.0);
        c.setMaintenanceMargin(10800.0);
        c.setActive(true);
        c.setFrontMonth(fullSymbol.equals("ESM5"));
        return c;
    }
}
