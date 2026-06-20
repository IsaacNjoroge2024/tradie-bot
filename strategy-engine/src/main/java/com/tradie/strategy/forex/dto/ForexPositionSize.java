package com.tradie.strategy.forex.dto;

/**
 * Result of a forex position size calculation.
 */
public record ForexPositionSize(
        double lots,            // Position size in standard lots (e.g., 0.5)
        double units,           // Position size in currency units (e.g., 50,000)
        String lotType,         // "STANDARD", "MINI", or "MICRO"
        double riskAmount,      // Dollar amount risked on the trade
        double pipValue,        // USD pip value for the calculated lot size
        double marginRequired   // USD margin required to open the position
) {}
