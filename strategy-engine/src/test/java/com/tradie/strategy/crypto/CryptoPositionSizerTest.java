package com.tradie.strategy.crypto;

import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import com.tradie.strategy.crypto.dto.CryptoPositionSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoPositionSizerTest {

    @Mock
    private CryptoAssetService assetService;

    private CryptoPositionSizer sizer;

    private static final CryptoAssetSpec BTC_SPEC =
            new CryptoAssetSpec("BTC", "Bitcoin", 0.0001, 0.0001, 2, 3.0, true);

    private static final CryptoAssetSpec ETH_SPEC =
            new CryptoAssetSpec("ETH", "Ethereum", 0.001, 0.001, 2, 3.5, true);

    @BeforeEach
    void setUp() {
        sizer = new CryptoPositionSizer(assetService);
    }

    // ─── calculate ────────────────────────────────────────────────────────────

    @Test
    void calculate_btc_adjustsForVolatilityAndReturnsCorrectQuantity() {
        // $10,000 account, 2% risk, BTC volMult=3.0
        // adjustedRisk = 2/3 = 0.667%, riskAmount = $66.67
        // entry=42500, stop=40375 → priceRisk=2125
        // qty = 66.67 / 2125 ≈ 0.03137 → rounded to 0.0001 increment = 0.0313
        when(assetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);

        CryptoPositionSize result = sizer.calculate("BTC", 10000.0, 2.0, 42500.0, 40375.0);

        assertThat(result.symbol()).isEqualTo("BTC");
        assertThat(result.adjustedRiskPct()).isCloseTo(2.0 / 3.0, offset(0.001));
        assertThat(result.riskAmount()).isCloseTo(66.67, offset(0.1));
        assertThat(result.quantity()).isGreaterThan(0.0);
        assertThat(result.quantity()).isLessThan(0.1);
        assertThat(result.notionalValue()).isCloseTo(result.quantity() * 42500.0, offset(0.1));
    }

    @Test
    void calculate_eth_higherVolatility_reducesPositionFurther() {
        // ETH volMult=3.5 → adjustedRisk=2/3.5≈0.571%, riskAmount=$57.14
        // entry=2000, stop=1900 → priceRisk=100
        // qty = 57.14 / 100 = 0.5714 → rounded to 0.001 increment = 0.571
        when(assetService.getAssetSpecOrDefault("ETH")).thenReturn(ETH_SPEC);

        CryptoPositionSize result = sizer.calculate("ETH", 10000.0, 2.0, 2000.0, 1900.0);

        assertThat(result.adjustedRiskPct()).isCloseTo(2.0 / 3.5, offset(0.001));
        assertThat(result.quantity()).isCloseTo(0.571, offset(0.001));
    }

    @Test
    void calculate_zeroStopLossDistance_returnsMinOrderSize() {
        when(assetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);

        // entry == stop → priceRisk = 0 → falls back to minOrderSize
        CryptoPositionSize result = sizer.calculate("BTC", 10000.0, 2.0, 42500.0, 42500.0);

        assertThat(result.quantity()).isEqualTo(BTC_SPEC.minOrderSize());
    }

    @Test
    void calculate_quantityBelowMinOrderSize_floorsToMinOrderSize() {
        // Very small account with large stop distance → raw qty < minOrderSize
        // $100 account, 2% risk, volMult=3 → riskAmount=$0.67
        // entry=42500, stop=10000 → priceRisk=32500
        // raw qty = 0.67/32500 ≈ 0.0000206 < minOrderSize(0.0001) → floored to 0.0001
        when(assetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);

        CryptoPositionSize result = sizer.calculate("BTC", 100.0, 2.0, 42500.0, 10000.0);

        assertThat(result.quantity()).isEqualTo(BTC_SPEC.minOrderSize());
    }

    @Test
    void calculate_notAvailableOnIbkr_throwsIllegalArgumentException() {
        CryptoAssetSpec unavailable = new CryptoAssetSpec("XYZ", "Unknown", 0.01, 0.01, 2, 3.0, false);
        when(assetService.getAssetSpecOrDefault("XYZ")).thenReturn(unavailable);

        assertThatThrownBy(() -> sizer.calculate("XYZ", 10000.0, 2.0, 1000.0, 950.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available on IBKR");
    }

    @Test
    void calculate_largeAccount_scalesPositionCorrectly() {
        // $100,000 account, 2% risk, BTC volMult=3 → riskAmount=$666.67
        // entry=42500, stop=40375 → priceRisk=2125
        // qty = 666.67 / 2125 ≈ 0.3137
        when(assetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);

        CryptoPositionSize small = sizer.calculate("BTC", 10000.0, 2.0, 42500.0, 40375.0);
        CryptoPositionSize large = sizer.calculate("BTC", 100000.0, 2.0, 42500.0, 40375.0);

        assertThat(large.quantity()).isCloseTo(small.quantity() * 10, offset(0.001));
    }

    // ─── roundToIncrement ─────────────────────────────────────────────────────

    @Test
    void roundToIncrement_btcPrecision_roundsDownCorrectly() {
        assertThat(sizer.roundToIncrement(0.03137, 0.0001)).isCloseTo(0.0313, offset(1e-9));
    }

    @Test
    void roundToIncrement_ethPrecision_roundsDownCorrectly() {
        assertThat(sizer.roundToIncrement(0.5714, 0.001)).isCloseTo(0.571, offset(1e-9));
    }

    @Test
    void roundToIncrement_exactMultiple_returnsUnchanged() {
        assertThat(sizer.roundToIncrement(0.0500, 0.001)).isCloseTo(0.050, offset(1e-9));
    }

    @Test
    void roundToIncrement_zeroIncrement_returnsQuantityUnchanged() {
        assertThat(sizer.roundToIncrement(0.123, 0.0)).isEqualTo(0.123);
    }

    // ─── formatQuantity ───────────────────────────────────────────────────────

    @Test
    void formatQuantity_btc_formatsToTwoDecimals() {
        when(assetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);

        String formatted = sizer.formatQuantity("BTC", 0.0312);
        assertThat(formatted).isEqualTo("0.03");
    }

    @Test
    void formatQuantity_eth_formatsToTwoDecimals() {
        when(assetService.getAssetSpecOrDefault("ETH")).thenReturn(ETH_SPEC);

        String formatted = sizer.formatQuantity("ETH", 0.5714);
        assertThat(formatted).isEqualTo("0.57");
    }
}
