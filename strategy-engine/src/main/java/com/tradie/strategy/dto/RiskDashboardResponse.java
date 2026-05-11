package com.tradie.strategy.dto;

import java.math.BigDecimal;
import java.util.List;

public record RiskDashboardResponse(
        double portfolioHeatPct,
        double riskCapacityRemainingPct,
        List<PortfolioHeatData.PositionHeatEntry> openPositions,
        int openPositionCount,
        BigDecimal dailyPnl,
        long dailyTradeCount,
        long losingStreak
) {}
