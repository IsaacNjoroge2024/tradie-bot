package com.tradie.strategy.forex.dto;

import java.time.DayOfWeek;

/**
 * Overnight swap (rollover) rates for a currency pair.
 * Swap points are expressed per standard lot (positive = credit, negative = debit).
 */
public record SwapRate(
        String pair,
        double longSwapPoints,   // Points per lot charged/credited for long positions
        double shortSwapPoints,  // Points per lot charged/credited for short positions
        DayOfWeek tripleSwapDay  // Day the T+2 settlement causes triple swap (usually WEDNESDAY)
) {}
