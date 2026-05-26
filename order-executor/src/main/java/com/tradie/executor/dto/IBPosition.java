package com.tradie.executor.dto;

public record IBPosition(
        String symbol,
        String exchange,
        double quantity,
        double avgCost,
        double marketValue,
        double unrealizedPnL
) {}
