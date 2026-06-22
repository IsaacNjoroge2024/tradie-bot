package com.tradie.strategy.forex;

import com.tradie.strategy.config.ForexProperties;
import com.tradie.strategy.forex.dto.ForexPositionSize;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexPositionSizerTest {

    @Mock
    private ForexPipCalculator pipCalculator;

    @Mock
    private ForexMarginCalculator marginCalculator;

    private ForexProperties forexProperties;
    private ForexPositionSizer sizer;

    @BeforeEach
    void setUp() {
        forexProperties = new ForexProperties();
        forexProperties.setMinLotSize(0.01);
        forexProperties.setMaxLotSize(10.0);
        sizer = new ForexPositionSizer(pipCalculator, marginCalculator, forexProperties);
    }

    // ─── calculate ────────────────────────────────────────────────────────────

    @Test
    void calculate_standardRisk_returnsCorrectLots() {
        // $10,000 account, 2% risk = $200
        // 20 pips stop, $10 pip value per lot → lots = 200 / (20 × 10) = 1.0
        when(pipCalculator.getPipValue(anyString(), anyDouble(), anyDouble())).thenReturn(10.0);
        when(marginCalculator.calculateMargin(anyString(), anyDouble(), anyDouble()))
                .thenReturn(2200.0);

        ForexPositionSize result = sizer.calculate("EURUSD", 10000.0, 2.0, 1.1, 20.0);

        assertThat(result.lots()).isCloseTo(1.0, Offset.offset(0.01));
        assertThat(result.units()).isCloseTo(100000.0, Offset.offset(1.0));
        assertThat(result.riskAmount()).isCloseTo(200.0, Offset.offset(0.01));
        assertThat(result.lotType()).isEqualTo("STANDARD");
    }

    @Test
    void calculate_smallRisk_returnsMiniLot() {
        // $1,000 account, 2% risk = $20, 20 pips, $10 pipValue → 0.1 lots (MINI)
        when(pipCalculator.getPipValue(anyString(), anyDouble(), anyDouble())).thenReturn(10.0);
        when(marginCalculator.calculateMargin(anyString(), anyDouble(), anyDouble()))
                .thenReturn(22.0);

        ForexPositionSize result = sizer.calculate("EURUSD", 1000.0, 2.0, 1.1, 20.0);

        assertThat(result.lots()).isCloseTo(0.10, Offset.offset(0.001));
        assertThat(result.units()).isCloseTo(10_000.0, Offset.offset(1.0));
        assertThat(result.lotType()).isEqualTo("MINI");
    }

    @Test
    void calculate_zeroStopLossPips_returnsMinLot() {
        when(pipCalculator.getPipValue(anyString(), anyDouble(), anyDouble())).thenReturn(10.0);
        when(marginCalculator.calculateMargin(anyString(), anyDouble(), anyDouble())).thenReturn(22.0);

        ForexPositionSize result = sizer.calculate("EURUSD", 10000.0, 2.0, 1.1, 0.0);

        assertThat(result.lots()).isEqualTo(forexProperties.getMinLotSize());
    }

    @Test
    void calculate_veryLargeAccount_cappedAtMaxLot() {
        // $10M account, 2% risk = $200,000, 5 pips, $10 pipValue → 400 lots → capped at 10.0
        when(pipCalculator.getPipValue(anyString(), anyDouble(), anyDouble())).thenReturn(10.0);
        when(marginCalculator.calculateMargin(anyString(), anyDouble(), anyDouble()))
                .thenReturn(22000.0);

        ForexPositionSize result = sizer.calculate("EURUSD", 10_000_000.0, 2.0, 1.1, 5.0);

        assertThat(result.lots()).isEqualTo(forexProperties.getMaxLotSize());
    }

    @Test
    void calculate_marginAndPipValuePopulated() {
        when(pipCalculator.getPipValue(anyString(), anyDouble(), anyDouble())).thenReturn(5.0);
        when(marginCalculator.calculateMargin(anyString(), anyDouble(), anyDouble()))
                .thenReturn(500.0);

        ForexPositionSize result = sizer.calculate("EURUSD", 10000.0, 2.0, 1.1, 20.0);

        assertThat(result.marginRequired()).isCloseTo(500.0, Offset.offset(0.01));
        assertThat(result.pipValue()).isCloseTo(5.0, Offset.offset(0.01));
    }

    // ─── getLotType ───────────────────────────────────────────────────────────

    @Test
    void getLotType_oneOrMore_returnsSTANDARD() {
        assertThat(sizer.getLotType(1.0)).isEqualTo("STANDARD");
        assertThat(sizer.getLotType(2.5)).isEqualTo("STANDARD");
    }

    @Test
    void getLotType_miniRange_returnsMINI() {
        assertThat(sizer.getLotType(0.1)).isEqualTo("MINI");
        assertThat(sizer.getLotType(0.5)).isEqualTo("MINI");
    }

    @Test
    void getLotType_microRange_returnsMICRO() {
        assertThat(sizer.getLotType(0.01)).isEqualTo("MICRO");
        assertThat(sizer.getLotType(0.05)).isEqualTo("MICRO");
    }

    // ─── roundToLotStep ───────────────────────────────────────────────────────

    @Test
    void roundToLotStep_floorsToStep() {
        // 0.0173 lots → floor to 0.01
        double rounded = sizer.roundToLotStep(0.0173);
        assertThat(rounded).isCloseTo(0.01, Offset.offset(0.0001));
    }

    @Test
    void roundToLotStep_exactStep_unchanged() {
        double rounded = sizer.roundToLotStep(0.50);
        assertThat(rounded).isCloseTo(0.50, Offset.offset(0.0001));
    }
}
