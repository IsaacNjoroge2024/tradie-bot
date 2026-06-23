package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContinuousContractServiceTest {

    @Mock
    private FuturesContractRepository repository;

    private ContinuousContractService service;

    @BeforeEach
    void setUp() {
        service = new ContinuousContractService(repository);
    }

    // ─── computeAdjustmentFactors ─────────────────────────────────────────────

    @Test
    void computeAdjustmentFactors_twoContracts_returnsCorrectFactor() {
        // ESH5 → ESM5 rollover; ratio = 5200 / 5000 = 1.04
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        double[] factors = service.computeAdjustmentFactors("ES", new double[]{1.04});

        assertThat(factors).hasSize(2);
        assertThat(factors[0]).isCloseTo(1.04, offset(0.0001)); // oldest contract adjusted
        assertThat(factors[1]).isEqualTo(1.0);                  // front month = 1.0
    }

    @Test
    void computeAdjustmentFactors_threeContracts_accumulatesRatios() {
        // ESZ4 → ESH5 ratio = 1.02, ESH5 → ESM5 ratio = 1.03
        // ESZ4 factor = 1.02 * 1.03 = 1.0506, ESH5 factor = 1.03, ESM5 factor = 1.0
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        buildContract("ESZ4", LocalDate.of(2024, 12, 20)),
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        double[] factors = service.computeAdjustmentFactors("ES", new double[]{1.02, 1.03});

        assertThat(factors).hasSize(3);
        assertThat(factors[2]).isEqualTo(1.0);
        assertThat(factors[1]).isCloseTo(1.03, offset(0.0001));
        assertThat(factors[0]).isCloseTo(1.02 * 1.03, offset(0.0001));
    }

    @Test
    void computeAdjustmentFactors_ratioBelow1_adjustsDown() {
        // Contango roll: next contract is CHEAPER (ratio < 1)
        when(repository.findBySymbolAndActiveTrue("CL"))
                .thenReturn(List.of(
                        buildContract("CLN5", LocalDate.of(2025, 6, 20)),
                        buildContract("CLQ5", LocalDate.of(2025, 7, 22))
                ));

        double[] factors = service.computeAdjustmentFactors("CL", new double[]{0.98});

        assertThat(factors[0]).isCloseTo(0.98, offset(0.0001));
        assertThat(factors[1]).isEqualTo(1.0);
    }

    @Test
    void computeAdjustmentFactors_fewerThanTwoContracts_returnsSingleFactor1() {
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(buildContract("ESM5", LocalDate.of(2025, 6, 20))));

        double[] factors = service.computeAdjustmentFactors("ES", new double[]{});

        assertThat(factors).containsExactly(1.0);
    }

    @Test
    void computeAdjustmentFactors_noContracts_returnsSingleFactor1() {
        when(repository.findBySymbolAndActiveTrue("ES")).thenReturn(List.of());

        double[] factors = service.computeAdjustmentFactors("ES", new double[]{});

        assertThat(factors).containsExactly(1.0);
    }

    @Test
    void computeAdjustmentFactors_wrongRatiosLength_throwsException() {
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        assertThatThrownBy(() -> service.computeAdjustmentFactors("ES", new double[]{1.02, 1.03}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 1 rollover ratios");
    }

    @Test
    void computeAdjustmentFactors_nonPositiveRatio_throwsException() {
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        assertThatThrownBy(() -> service.computeAdjustmentFactors("ES", new double[]{0.0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void computeAdjustmentFactors_nullRatios_throwsException() {
        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        assertThatThrownBy(() -> service.computeAdjustmentFactors("ES", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computeAdjustmentFactors_contractsWithoutExpirationDate_skipped() {
        // Contract with null expiration is excluded from the sorted list
        FuturesContract noDate = new FuturesContract();
        noDate.setFullSymbol("ESX5");
        noDate.setExpirationDate(null);

        when(repository.findBySymbolAndActiveTrue("ES"))
                .thenReturn(List.of(
                        noDate,
                        buildContract("ESH5", LocalDate.of(2025, 3, 21)),
                        buildContract("ESM5", LocalDate.of(2025, 6, 20))
                ));

        // Only 2 dated contracts remain → 1 ratio expected
        double[] factors = service.computeAdjustmentFactors("ES", new double[]{1.02});

        assertThat(factors).hasSize(2);
        assertThat(factors[0]).isCloseTo(1.02, offset(0.0001));
        assertThat(factors[1]).isEqualTo(1.0);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private FuturesContract buildContract(String fullSymbol, LocalDate expirationDate) {
        FuturesContract c = new FuturesContract();
        c.setFullSymbol(fullSymbol);
        c.setSymbol(fullSymbol.substring(0, fullSymbol.length() - 2));
        c.setExpirationDate(expirationDate);
        c.setActive(true);
        return c;
    }
}
