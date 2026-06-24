package com.tradie.strategy.crypto.dto;

/**
 * Immutable spec for a crypto asset supported via IBKR Paxos.
 */
public record CryptoAssetSpec(
        String symbol,
        String name,
        double minOrderSize,
        double sizeIncrement,
        int pricePrecision,
        double volatilityMultiplier,
        boolean availableOnIbkr
) {}
