package com.tradie.alert.service;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Computes daily trading metrics from the positions table for the daily summary report
 * and the /pnl Telegram command.
 */
@Service
public class DailySummaryService {

    private static final ZoneId TRADING_ZONE = ZoneId.of("America/New_York");

    private final PositionRepository positionRepository;

    public DailySummaryService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public DailySummaryData getDailySummary() {
        Instant startOfDay = LocalDate.now(TRADING_ZONE)
                .atStartOfDay(TRADING_ZONE)
                .toInstant();

        List<Position> closedToday = positionRepository
                .findByStatusAndClosedAtAfter(Position.PositionStatus.CLOSED, startOfDay);

        int wins = 0;
        int losses = 0;
        int totalTrades = 0;
        double totalPnl = 0;
        double bestTrade = 0;
        double worstTrade = 0;
        boolean first = true;

        for (Position p : closedToday) {
            if (p.getRealizedPnl() == null) continue;
            double pnl = p.getRealizedPnl().doubleValue();
            totalTrades++;
            totalPnl += pnl;
            if (pnl > 0) wins++;
            else if (pnl < 0) losses++;
            if (first) {
                bestTrade = pnl;
                worstTrade = pnl;
                first = false;
            } else {
                if (pnl > bestTrade) bestTrade = pnl;
                if (pnl < worstTrade) worstTrade = pnl;
            }
        }
        double winRate = totalTrades > 0 ? (double) wins / totalTrades * 100 : 0;
        long openPositions = positionRepository.countByStatus(Position.PositionStatus.OPEN);

        return new DailySummaryData(totalPnl, totalTrades, wins, losses, winRate,
                bestTrade, worstTrade, (int) openPositions);
    }

    public record DailySummaryData(
            double totalPnl,
            int totalTrades,
            int wins,
            int losses,
            double winRate,
            double bestTrade,
            double worstTrade,
            int openPositions
    ) {}
}
