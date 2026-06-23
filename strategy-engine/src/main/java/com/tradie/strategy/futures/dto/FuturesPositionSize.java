package com.tradie.strategy.futures.dto;

/**
 * Result of a futures position size calculation.
 */
public record FuturesPositionSize(
        int contracts,
        double notionalValue,
        double riskAmount,
        double ticksToStop,
        double marginRequired
) {}
