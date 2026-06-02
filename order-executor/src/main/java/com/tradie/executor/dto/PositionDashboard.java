package com.tradie.executor.dto;

import java.util.List;

public record PositionDashboard(
        int openPositionCount,
        double totalUnrealizedPnL,
        double totalUnrealizedPnLPct,
        double dayPnL,
        double weekPnL,
        List<PositionSummary> positions
) {}
