package com.tradie.strategy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioHeatData(
        double totalHeatPct,
        List<PositionHeatEntry> positions,
        Instant calculatedAt
) {
    public PortfolioHeatData {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    public record PositionHeatEntry(
            String symbol,
            BigDecimal riskAmount,
            double riskPct
    ) {}
}
