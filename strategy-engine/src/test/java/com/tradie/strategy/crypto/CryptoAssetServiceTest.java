package com.tradie.strategy.crypto;

import com.tradie.common.entity.CryptoAsset;
import com.tradie.common.repository.CryptoAssetRepository;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoAssetServiceTest {

    @Mock
    private CryptoAssetRepository repository;

    private CryptoAssetService service;

    @BeforeEach
    void setUp() {
        service = new CryptoAssetService(repository);
    }

    // ─── getAssetSpec ─────────────────────────────────────────────────────────

    @Test
    void getAssetSpec_foundInDb_returnsSpec() {
        when(repository.findBySymbolAndAvailableOnIbkrTrue("BTC"))
                .thenReturn(Optional.of(buildEntity("BTC", "Bitcoin", 0.0001, 0.0001, 2, 3.0)));

        Optional<CryptoAssetSpec> result = service.getAssetSpec("BTC");

        assertThat(result).isPresent();
        assertThat(result.get().symbol()).isEqualTo("BTC");
        assertThat(result.get().volatilityMultiplier()).isEqualTo(3.0);
    }

    @Test
    void getAssetSpec_notInDb_returnsEmpty() {
        when(repository.findBySymbolAndAvailableOnIbkrTrue("XRP"))
                .thenReturn(Optional.empty());

        Optional<CryptoAssetSpec> result = service.getAssetSpec("XRP");

        assertThat(result).isEmpty();
    }

    @Test
    void getAssetSpec_lowercaseSymbol_normalizesToUppercase() {
        when(repository.findBySymbolAndAvailableOnIbkrTrue("BTC"))
                .thenReturn(Optional.of(buildEntity("BTC", "Bitcoin", 0.0001, 0.0001, 2, 3.0)));

        Optional<CryptoAssetSpec> result = service.getAssetSpec("btc");

        assertThat(result).isPresent();
    }

    // ─── getAssetSpecOrDefault ────────────────────────────────────────────────

    @Test
    void getAssetSpecOrDefault_foundInDb_returnsDbSpec() {
        CryptoAsset entity = buildEntity("ETH", "Ethereum", 0.001, 0.001, 2, 3.5);
        when(repository.findBySymbolAndAvailableOnIbkrTrue("ETH")).thenReturn(Optional.of(entity));

        CryptoAssetSpec result = service.getAssetSpecOrDefault("ETH");

        assertThat(result.symbol()).isEqualTo("ETH");
        assertThat(result.volatilityMultiplier()).isEqualTo(3.5);
    }

    @Test
    void getAssetSpecOrDefault_notInDb_usesBuiltInFallback() {
        when(repository.findBySymbolAndAvailableOnIbkrTrue(anyString())).thenReturn(Optional.empty());

        CryptoAssetSpec result = service.getAssetSpecOrDefault("BTC");

        assertThat(result.symbol()).isEqualTo("BTC");
        assertThat(result.minOrderSize()).isEqualTo(0.0001);
        assertThat(result.volatilityMultiplier()).isEqualTo(3.0);
    }

    @Test
    void getAssetSpecOrDefault_unknownSymbolNotInDb_throwsIllegalArgumentException() {
        when(repository.findBySymbolAndAvailableOnIbkrTrue(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssetSpecOrDefault("DOGE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOGE");
    }

    // ─── getAvailableAssets ───────────────────────────────────────────────────

    @Test
    void getAvailableAssets_returnsMappedSpecs() {
        when(repository.findByAvailableOnIbkrTrue()).thenReturn(List.of(
                buildEntity("BTC", "Bitcoin", 0.0001, 0.0001, 2, 3.0),
                buildEntity("ETH", "Ethereum", 0.001, 0.001, 2, 3.5)
        ));

        List<CryptoAssetSpec> result = service.getAvailableAssets();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
        assertThat(result.get(1).symbol()).isEqualTo("ETH");
    }

    // ─── getBuiltInSpec ───────────────────────────────────────────────────────

    @Test
    void getBuiltInSpec_btc_returnsCorrectSpec() {
        CryptoAssetSpec spec = service.getBuiltInSpec("BTC");
        assertThat(spec.symbol()).isEqualTo("BTC");
        assertThat(spec.minOrderSize()).isEqualTo(0.0001);
        assertThat(spec.sizeIncrement()).isEqualTo(0.0001);
        assertThat(spec.volatilityMultiplier()).isEqualTo(3.0);
        assertThat(spec.availableOnIbkr()).isTrue();
    }

    @Test
    void getBuiltInSpec_eth_returnsCorrectSpec() {
        CryptoAssetSpec spec = service.getBuiltInSpec("ETH");
        assertThat(spec.volatilityMultiplier()).isEqualTo(3.5);
        assertThat(spec.minOrderSize()).isEqualTo(0.001);
    }

    @Test
    void getBuiltInSpec_ltc_returnsCorrectSpec() {
        CryptoAssetSpec spec = service.getBuiltInSpec("LTC");
        assertThat(spec.volatilityMultiplier()).isEqualTo(4.0);
        assertThat(spec.minOrderSize()).isEqualTo(0.01);
    }

    @Test
    void getBuiltInSpec_bch_returnsCorrectSpec() {
        CryptoAssetSpec spec = service.getBuiltInSpec("BCH");
        assertThat(spec.volatilityMultiplier()).isEqualTo(4.0);
        assertThat(spec.symbol()).isEqualTo("BCH");
    }

    @Test
    void getBuiltInSpec_caseInsensitive_returnsSpec() {
        CryptoAssetSpec spec = service.getBuiltInSpec("btc");
        assertThat(spec.symbol()).isEqualTo("BTC");
    }

    @Test
    void getBuiltInSpec_unknownSymbol_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getBuiltInSpec("SHIB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHIB");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private CryptoAsset buildEntity(String symbol, String name, double minOrderSize,
                                     double sizeIncrement, int pricePrecision,
                                     double volatilityMultiplier) {
        CryptoAsset asset = new CryptoAsset();
        asset.setSymbol(symbol);
        asset.setName(name);
        asset.setMinOrderSize(minOrderSize);
        asset.setSizeIncrement(sizeIncrement);
        asset.setPricePrecision(pricePrecision);
        asset.setVolatilityMultiplier(volatilityMultiplier);
        asset.setAvailableOnIbkr(true);
        return asset;
    }
}
