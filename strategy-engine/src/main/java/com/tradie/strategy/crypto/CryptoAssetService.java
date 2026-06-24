package com.tradie.strategy.crypto;

import com.tradie.common.entity.CryptoAsset;
import com.tradie.common.repository.CryptoAssetRepository;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads crypto asset specifications from the database with built-in fallbacks
 * for the four IBKR Paxos-supported assets (BTC, ETH, LTC, BCH).
 */
@Service
public class CryptoAssetService {

    private static final Logger log = LoggerFactory.getLogger(CryptoAssetService.class);

    private final CryptoAssetRepository repository;

    public CryptoAssetService(CryptoAssetRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the spec for a given symbol only if it is present and available on IBKR.
     */
    public Optional<CryptoAssetSpec> getAssetSpec(String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        return repository.findBySymbolAndAvailableOnIbkrTrue(symbol.toUpperCase(Locale.ROOT))
                .map(this::toSpec);
    }

    /**
     * Returns the spec for a given symbol, distinguishing disabled rows from absent ones.
     *
     * <ul>
     *   <li>If the DB row exists and {@code availableOnIbkr=true} → returns the DB spec.</li>
     *   <li>If the DB row exists but {@code availableOnIbkr=false} → throws, so a disabled
     *       asset is never silently re-enabled by the built-in fallback.</li>
     *   <li>If the DB has no row for the symbol → falls back to the built-in spec.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the symbol is explicitly disabled or unknown
     */
    public CryptoAssetSpec getAssetSpecOrDefault(String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        String upper = symbol.toUpperCase(Locale.ROOT);

        Optional<CryptoAsset> found = repository.findById(upper);
        if (found.isPresent()) {
            CryptoAsset asset = found.get();
            if (!asset.isAvailableOnIbkr()) {
                throw new IllegalArgumentException("Crypto asset is disabled on IBKR: " + upper);
            }
            return toSpec(asset);
        }

        log.warn("No crypto asset found in DB for {}, using built-in spec", upper);
        return getBuiltInSpec(upper);
    }

    /**
     * Returns all crypto assets that are available on IBKR.
     */
    public List<CryptoAssetSpec> getAvailableAssets() {
        return repository.findByAvailableOnIbkrTrue().stream()
                .map(this::toSpec)
                .collect(Collectors.toList());
    }

    /**
     * Hard-coded fallback specs for IBKR Paxos-supported crypto assets.
     * Used only when the DB has no matching row for the symbol.
     */
    CryptoAssetSpec getBuiltInSpec(String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        return switch (symbol.toUpperCase(Locale.ROOT)) {
            case "BTC" -> new CryptoAssetSpec("BTC", "Bitcoin",
                    BigDecimal.valueOf(0.0001), BigDecimal.valueOf(0.0001), 2, 3.0, true);
            case "ETH" -> new CryptoAssetSpec("ETH", "Ethereum",
                    BigDecimal.valueOf(0.001),  BigDecimal.valueOf(0.001),  2, 3.5, true);
            case "LTC" -> new CryptoAssetSpec("LTC", "Litecoin",
                    BigDecimal.valueOf(0.01),   BigDecimal.valueOf(0.01),   2, 4.0, true);
            case "BCH" -> new CryptoAssetSpec("BCH", "Bitcoin Cash",
                    BigDecimal.valueOf(0.001),  BigDecimal.valueOf(0.001),  2, 4.0, true);
            default -> throw new IllegalArgumentException("Unsupported crypto symbol: " + symbol);
        };
    }

    private CryptoAssetSpec toSpec(CryptoAsset asset) {
        return new CryptoAssetSpec(
                asset.getSymbol(),
                asset.getName(),
                asset.getMinOrderSize(),
                asset.getSizeIncrement(),
                asset.getPricePrecision(),
                asset.getVolatilityMultiplier(),
                asset.isAvailableOnIbkr()
        );
    }
}
