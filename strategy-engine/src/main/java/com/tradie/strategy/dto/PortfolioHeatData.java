package com.tradie.strategy.dto;

import java.time.Instant;
import java.util.List;

public record PortfolioHeatData(
        double totalHeatPct,
        List<PositionHeatEntry> positions,
        Instant calculatedAt
) {
    public record PositionHeatEntry(
            String symbol,
            double riskAmount,
            double riskPct
    ) {}
}
