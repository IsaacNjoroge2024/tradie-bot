package com.tradie.strategy.crypto.dto;

/**
 * Result of a crypto position size calculation.
 *
 * <p>quantity is expressed as a fractional amount of the crypto asset
 * (e.g., 0.0023 BTC). riskAmount reflects the volatility-adjusted dollar risk.
 */
public record CryptoPositionSize(
        double quantity,
        double notionalValue,
        double riskAmount,
        double adjustedRiskPct,
        String symbol
) {}
