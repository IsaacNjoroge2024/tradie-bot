package com.tradie.strategy.crypto.dto;

import java.math.BigDecimal;

/**
 * Immutable spec for a crypto asset supported via IBKR Paxos.
 */
public record CryptoAssetSpec(
        String symbol,
        String name,
        BigDecimal minOrderSize,
        BigDecimal sizeIncrement,
        int pricePrecision,
        double volatilityMultiplier,
        boolean availableOnIbkr
) {}
