package com.tradie.alert.service;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                .findByStatusAndClosedAtGreaterThanEqual(Position.PositionStatus.CLOSED, startOfDay);

        int wins = 0;
        int losses = 0;
        int totalTrades = 0;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal bestTrade = null;
        BigDecimal worstTrade = null;

        for (Position p : closedToday) {
            if (p.getRealizedPnl() == null) continue;
            BigDecimal pnl = p.getRealizedPnl();
            totalTrades++;
            totalPnl = totalPnl.add(pnl);
            if (pnl.compareTo(BigDecimal.ZERO) >= 0) wins++;
            else losses++;
            if (bestTrade == null) {
                bestTrade = pnl;
                worstTrade = pnl;
            } else {
                if (pnl.compareTo(bestTrade) > 0) bestTrade = pnl;
                if (pnl.compareTo(worstTrade) < 0) worstTrade = pnl;
            }
        }

        double winRate = totalTrades > 0
                ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        long openPositions = positionRepository.countByStatus(Position.PositionStatus.OPEN);

        return new DailySummaryData(
                totalPnl,
                totalTrades,
                wins,
                losses,
                winRate,
                bestTrade != null ? bestTrade : BigDecimal.ZERO,
                worstTrade != null ? worstTrade : BigDecimal.ZERO,
                (int) openPositions);
    }

    public record DailySummaryData(
            BigDecimal totalPnl,
            int totalTrades,
            int wins,
            int losses,
            double winRate,
            BigDecimal bestTrade,
            BigDecimal worstTrade,
            int openPositions
    ) {}
}
