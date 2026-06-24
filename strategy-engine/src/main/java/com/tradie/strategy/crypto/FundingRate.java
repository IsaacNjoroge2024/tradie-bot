package com.tradie.strategy.crypto;

import java.time.Instant;

/**
 * Represents a perpetual funding rate snapshot from an external exchange.
 *
 * <p>Not applicable for IBKR spot crypto, but useful for cross-exchange sentiment:
 * extreme positive rates often precede long squeezes; extreme negative rates precede short squeezes.
 *
 * <p>Positive rate = longs pay shorts. Negative rate = shorts pay longs.
 */
public record FundingRate(
        String symbol,
        String exchange,
        double rate,
        Instant nextFundingTime
) {}
