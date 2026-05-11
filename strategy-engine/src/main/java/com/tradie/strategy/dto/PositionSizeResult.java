package com.tradie.strategy.dto;

import java.math.BigDecimal;
import java.util.List;

public record PositionSizeResult(
        BigDecimal quantity,
        BigDecimal riskAmount,
        double riskPercentage,
        String sizingMethod,
        String assetClass,
        List<String> adjustments,
        boolean valid,
        double portfolioHeatBefore,
        double portfolioHeatAfter
) {
    public PositionSizeResult {
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}
