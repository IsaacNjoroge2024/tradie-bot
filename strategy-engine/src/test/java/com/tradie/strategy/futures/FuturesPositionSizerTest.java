package com.tradie.strategy.futures;

import com.tradie.strategy.config.FuturesProperties;
import com.tradie.strategy.futures.dto.FuturesPositionSize;
import com.tradie.strategy.futures.dto.FuturesSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuturesPositionSizerTest {

    @Mock
    private FuturesContractService contractService;

    private FuturesProperties futuresProperties;
    private FuturesPositionSizer sizer;

    private static final FuturesSpec ES_SPEC =
            new FuturesSpec("ES", "ESM5", "202506", "CME", 50.0, 0.25, 12.50, null, 12000.0, 10800.0);

    @BeforeEach
    void setUp() {
        futuresProperties = new FuturesProperties();
        futuresProperties.setMarginBufferPct(20.0);
        futuresProperties.setDefaultRolloverDays(8);
        sizer = new FuturesPositionSizer(contractService, futuresProperties);
    }

    // ─── calculate ────────────────────────────────────────────────────────────

    @Test
    void calculate_standardRisk_returnsCorrectContracts() {
        // $100,000 account, 2% risk = $2,000
        // entry=5000, stop=4990 → distance=10 → ticks=10/0.25=40 → riskPerContract=40×12.50=$500
        // contracts = floor(2000/500) = 4
        when(contractService.getFrontMonthContract("ES")).thenReturn(Optional.of(ES_SPEC));

        FuturesPositionSize result = sizer.calculate("ES", 100000.0, 2.0, 5000.0, 4990.0);

        assertThat(result.contracts()).isEqualTo(4);
        assertThat(result.riskAmount()).isCloseTo(2000.0, offset(0.01));
        assertThat(result.ticksToStop()).isCloseTo(40.0, offset(0.01));
        assertThat(result.marginRequired()).isCloseTo(48000.0, offset(0.01)); // 4 × 12000
        assertThat(result.notionalValue()).isCloseTo(1000000.0, offset(1.0)); // 4 × 5000 × 50
    }

    @Test
    void calculate_minimumOneContract_whenRiskTooSmall() {
        // $20,000 account, 0.1% risk = $20
        // ticks = (5000 - 4990) / 0.25 = 40 → riskPerContract = 40 × 12.50 = $500
        // floor(20 / 500) = 0 → clamped to minimum 1
        // margin check: $12,000 × 1 = $12,000 ≤ $20,000 × 0.80 = $16,000 → passes
        when(contractService.getFrontMonthContract("ES")).thenReturn(Optional.of(ES_SPEC));

        FuturesPositionSize result = sizer.calculate("ES", 20000.0, 0.1, 5000.0, 4990.0);

        assertThat(result.contracts()).isEqualTo(1);
    }

    @Test
    void calculate_zeroStopLossDistance_returnsOneContract() {
        when(contractService.getFrontMonthContract("ES")).thenReturn(Optional.of(ES_SPEC));

        // entry == stop → distance = 0 → riskPerContract = 0 → clamp to 1
        FuturesPositionSize result = sizer.calculate("ES", 100000.0, 2.0, 5000.0, 5000.0);

        assertThat(result.contracts()).isEqualTo(1);
    }

    @Test
    void calculate_noDbRecord_fallsBackToBuiltInSpec() {
        when(contractService.getFrontMonthContract("ES")).thenReturn(Optional.empty());

        // Uses built-in ES spec: multiplier=50, tickSize=0.25, tickValue=12.50
        FuturesPositionSize result = sizer.calculate("ES", 100000.0, 2.0, 5000.0, 4990.0);

        assertThat(result.contracts()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void calculate_marginExceedsBuffer_throwsInsufficientMarginException() {
        // $10,000 account, 2% risk = $200
        // 1 contract × $12,000 margin > $10,000 × 0.80 = $8,000
        when(contractService.getFrontMonthContract("ES")).thenReturn(Optional.of(ES_SPEC));

        assertThatThrownBy(() -> sizer.calculate("ES", 10000.0, 2.0, 5000.0, 4990.0))
                .isInstanceOf(InsufficientMarginException.class)
                .hasMessageContaining("margin");
    }

    @Test
    void calculate_clSpec_usesCorrectTickValue() {
        // CL: multiplier=1000, tickSize=0.01, tickValue=10
        // $50,000 account, 2% risk = $1,000
        // entry=70, stop=69.5 → distance=0.5 → ticks=50 → riskPerContract=50×10=$500
        // contracts=floor(1000/500)=2
        FuturesSpec clSpec = new FuturesSpec("CL", "CLN5", "202507", "NYMEX",
                1000.0, 0.01, 10.0, null, 8000.0, 7200.0);
        when(contractService.getFrontMonthContract("CL")).thenReturn(Optional.of(clSpec));

        FuturesPositionSize result = sizer.calculate("CL", 50000.0, 2.0, 70.0, 69.5);

        assertThat(result.contracts()).isEqualTo(2);
        assertThat(result.ticksToStop()).isCloseTo(50.0, offset(0.01));
        assertThat(result.marginRequired()).isCloseTo(16000.0, offset(0.01)); // 2 × 8000
    }

    // ─── getBuiltInSpec ───────────────────────────────────────────────────────

    @Test
    void getBuiltInSpec_es_returnsCorrectSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("ES");
        assertThat(spec.multiplier()).isEqualTo(50.0);
        assertThat(spec.tickSize()).isEqualTo(0.25);
        assertThat(spec.tickValue()).isEqualTo(12.50);
        assertThat(spec.exchange()).isEqualTo("CME");
        assertThat(spec.initialMargin()).isEqualTo(12000.0);
    }

    @Test
    void getBuiltInSpec_nq_returnsCorrectSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("NQ");
        assertThat(spec.multiplier()).isEqualTo(20.0);
        assertThat(spec.tickValue()).isEqualTo(5.0);
    }

    @Test
    void getBuiltInSpec_cl_returnsNymexSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("CL");
        assertThat(spec.exchange()).isEqualTo("NYMEX");
        assertThat(spec.multiplier()).isEqualTo(1000.0);
        assertThat(spec.tickValue()).isEqualTo(10.0);
    }

    @Test
    void getBuiltInSpec_gc_returnsComexSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("GC");
        assertThat(spec.exchange()).isEqualTo("COMEX");
        assertThat(spec.multiplier()).isEqualTo(100.0);
    }

    @Test
    void getBuiltInSpec_mes_returnsMicroSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("MES");
        assertThat(spec.multiplier()).isEqualTo(5.0);
        assertThat(spec.tickValue()).isEqualTo(1.25);
    }

    @Test
    void getBuiltInSpec_unknownSymbol_returnsDefaultEsLikeSpec() {
        FuturesSpec spec = sizer.getBuiltInSpec("UNKNOWN");
        assertThat(spec.multiplier()).isEqualTo(50.0);
        assertThat(spec.exchange()).isEqualTo("CME");
    }
}
