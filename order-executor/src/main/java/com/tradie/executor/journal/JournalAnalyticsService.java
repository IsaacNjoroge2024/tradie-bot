package com.tradie.executor.journal;

import com.tradie.common.entity.TradeJournal;
import com.tradie.executor.dto.PerformanceStats;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JournalAnalyticsService {

    public PerformanceStats calculateStats(List<TradeJournal> entries) {
        List<TradeJournal> closed = entries.stream()
                .filter(e -> e.getRealizedPnl() != null)
                .toList();

        int totalTrades = closed.size();
        int wins  = (int) closed.stream().filter(e -> e.getRealizedPnl() > 0).count();
        int losses = (int) closed.stream().filter(e -> e.getRealizedPnl() < 0).count();
        double winRate = totalTrades > 0 ? (double) wins / totalTrades : 0.0;

        double avgWin = closed.stream()
                .filter(e -> e.getRealizedPnl() > 0)
                .mapToDouble(TradeJournal::getRealizedPnl)
                .average().orElse(0.0);

        double avgLoss = closed.stream()
                .filter(e -> e.getRealizedPnl() < 0)
                .mapToDouble(TradeJournal::getRealizedPnl)
                .average().orElse(0.0);

        double totalWins = closed.stream()
                .filter(e -> e.getRealizedPnl() > 0)
                .mapToDouble(TradeJournal::getRealizedPnl)
                .sum();

        double totalLosses = Math.abs(closed.stream()
                .filter(e -> e.getRealizedPnl() < 0)
                .mapToDouble(TradeJournal::getRealizedPnl)
                .sum());

        double profitFactor = totalLosses > 0 ? totalWins / totalLosses : 0.0;

        double maxDrawdown = closed.stream()
                .filter(e -> e.getRealizedPnl() < 0)
                .mapToDouble(TradeJournal::getRealizedPnl)
                .min().orElse(0.0);

        double sharpeRatio = calculateSharpeRatio(closed);

        Map<String, PerformanceStats.StrategyStats> byStrategy = buildStrategyStats(closed);
        Map<String, Double> bySymbol = buildSymbolStats(closed);
        Map<DayOfWeek, Double> byDayOfWeek = buildDayOfWeekStats(closed);
        Map<Integer, Double> byHour = buildHourStats(closed);

        return new PerformanceStats(totalTrades, wins, losses, winRate, avgWin, avgLoss,
                profitFactor, maxDrawdown, sharpeRatio, byStrategy, bySymbol, byDayOfWeek, byHour);
    }

    private double calculateSharpeRatio(List<TradeJournal> closed) {
        if (closed.size() < 2) return 0.0;
        double[] pnls = closed.stream().mapToDouble(TradeJournal::getRealizedPnl).toArray();
        double mean = closed.stream().mapToDouble(TradeJournal::getRealizedPnl).average().orElse(0.0);
        double variance = 0.0;
        for (double pnl : pnls) {
            variance += Math.pow(pnl - mean, 2);
        }
        variance /= pnls.length;
        double stdDev = Math.sqrt(variance);
        return stdDev > 0 ? mean / stdDev : 0.0;
    }

    private Map<String, PerformanceStats.StrategyStats> buildStrategyStats(List<TradeJournal> closed) {
        return closed.stream()
                .filter(e -> e.getStrategy() != null)
                .collect(Collectors.groupingBy(
                        TradeJournal::getStrategy,
                        Collectors.collectingAndThen(Collectors.toList(), group -> {
                            int trades = group.size();
                            int wins = (int) group.stream().filter(e -> e.getRealizedPnl() > 0).count();
                            double winRate = trades > 0 ? (double) wins / trades : 0.0;
                            double totalPnl = group.stream().mapToDouble(TradeJournal::getRealizedPnl).sum();
                            return new PerformanceStats.StrategyStats(trades, wins, winRate, totalPnl);
                        })
                ));
    }

    private Map<String, Double> buildSymbolStats(List<TradeJournal> closed) {
        return closed.stream()
                .filter(e -> e.getSymbol() != null)
                .collect(Collectors.groupingBy(
                        TradeJournal::getSymbol,
                        Collectors.summingDouble(TradeJournal::getRealizedPnl)));
    }

    private Map<DayOfWeek, Double> buildDayOfWeekStats(List<TradeJournal> closed) {
        Map<DayOfWeek, Double> result = new LinkedHashMap<>();
        closed.stream()
                .filter(e -> e.getEntryTime() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEntryTime().atZone(ZoneOffset.UTC).getDayOfWeek(),
                        Collectors.summingDouble(TradeJournal::getRealizedPnl)))
                .forEach(result::put);
        return result;
    }

    private Map<Integer, Double> buildHourStats(List<TradeJournal> closed) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        closed.stream()
                .filter(e -> e.getEntryTime() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEntryTime().atZone(ZoneOffset.UTC).getHour(),
                        Collectors.summingDouble(TradeJournal::getRealizedPnl)))
                .forEach(result::put);
        return result;
    }
}
