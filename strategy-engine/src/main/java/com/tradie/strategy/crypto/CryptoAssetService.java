package com.tradie.strategy.crypto;

import com.tradie.common.entity.CryptoAsset;
import com.tradie.common.repository.CryptoAssetRepository;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * Returns the spec for a given symbol if it is available on IBKR.
     */
    public Optional<CryptoAssetSpec> getAssetSpec(String symbol) {
        return repository.findBySymbolAndAvailableOnIbkrTrue(symbol.toUpperCase())
                .map(this::toSpec);
    }

    /**
     * Returns the spec for a given symbol, falling back to built-in specs if the DB has no record.
     *
     * @throws IllegalArgumentException if the symbol is not found in DB or built-in specs
     */
    public CryptoAssetSpec getAssetSpecOrDefault(String symbol) {
        return repository.findBySymbolAndAvailableOnIbkrTrue(symbol.toUpperCase())
                .map(this::toSpec)
                .orElseGet(() -> {
                    log.warn("No crypto asset found in DB for {}, using built-in spec", symbol);
                    return getBuiltInSpec(symbol);
                });
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
        return switch (symbol.toUpperCase()) {
            case "BTC" -> new CryptoAssetSpec("BTC", "Bitcoin",      0.0001, 0.0001, 2, 3.0, true);
            case "ETH" -> new CryptoAssetSpec("ETH", "Ethereum",     0.001,  0.001,  2, 3.5, true);
            case "LTC" -> new CryptoAssetSpec("LTC", "Litecoin",     0.01,   0.01,   2, 4.0, true);
            case "BCH" -> new CryptoAssetSpec("BCH", "Bitcoin Cash", 0.001,  0.001,  2, 4.0, true);
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
