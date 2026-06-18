package com.tradie.executor.dto;

import java.time.DayOfWeek;
import java.util.Map;

public record PerformanceStats(
        int totalTrades,
        int wins,
        int losses,
        double winRate,
        double avgWin,
        double avgLoss,
        double profitFactor,
        double maxDrawdown,
        double sharpeRatio,
        Map<String, StrategyStats> byStrategy,
        Map<String, Double> bySymbol,
        Map<DayOfWeek, Double> byDayOfWeek,
        Map<Integer, Double> byHour
) {
    public record StrategyStats(int trades, int wins, double winRate, double totalPnl) {}
}
