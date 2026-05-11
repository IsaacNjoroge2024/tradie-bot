package com.tradie.strategy.dto;

import java.math.BigDecimal;
import java.util.List;

public record PositionSizeResult(
        BigDecimal quantity,
        BigDecimal riskAmount,
        double riskPercentage,
        String sizingMethod,
        List<String> adjustments,
        boolean valid,
        double portfolioHeatBefore,
        double portfolioHeatAfter
) {}
