package com.tradie.strategy.futures.dto;

import java.time.LocalDate;

/**
 * Immutable snapshot of a futures contract specification.
 */
public record FuturesSpec(
        String symbol,
        String fullSymbol,
        String contractMonth,    // YYYYMM format (e.g., "202506"); null for built-in defaults
        String exchange,
        double multiplier,
        double tickSize,
        double tickValue,
        LocalDate expirationDate,
        double initialMargin,
        double maintenanceMargin
) {}
