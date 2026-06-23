package com.tradie.strategy.futures.dto;

import java.time.LocalDate;

/**
 * Alert indicating a futures contract is approaching its rollover date.
 */
public record RolloverAlert(
        String symbol,
        String currentContract,
        String nextContract,
        LocalDate rolloverDate,
        int daysRemaining
) {}
