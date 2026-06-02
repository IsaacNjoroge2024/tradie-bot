package com.tradie.executor.dto;

import java.time.Duration;
import java.util.UUID;

public record PositionSummary(
        UUID id,
        String symbol,
        String side,
        double quantity,
        double entryPrice,
        double currentPrice,
        double unrealizedPnL,
        double unrealizedPnLPct,
        double stopLoss,
        double takeProfit,
        String strategy,
        Duration holdTime
) {}
