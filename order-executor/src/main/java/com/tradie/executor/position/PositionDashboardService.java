package com.tradie.executor.position;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.PositionDashboard;
import com.tradie.executor.dto.PositionSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Aggregates position data for the dashboard endpoint.
 */
@Service
public class PositionDashboardService {

    private final PositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final PnLCalculator pnlCalculator;

    public PositionDashboardService(
            PositionRepository positionRepository,
            MarketDataService marketDataService,
            PnLCalculator pnlCalculator) {
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
        this.pnlCalculator = pnlCalculator;
    }

    public PositionDashboard getDashboard() {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);

        List<PositionSummary> summaries = openPositions.stream()
                .map(this::toSummary)
                .toList();

        double totalUnrealizedPnL = summaries.stream()
                .mapToDouble(PositionSummary::unrealizedPnL)
                .sum();

        double totalCost = openPositions.stream()
                .mapToDouble(p -> p.getEntryPrice().multiply(p.getQuantity()).doubleValue())
                .sum();

        double totalUnrealizedPnLPct = totalCost > 0
                ? (totalUnrealizedPnL / totalCost) * 100 : 0.0;

        Instant startOfDay  = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

        double dayPnL  = sumRealizedPnl(startOfDay);
        double weekPnL = sumRealizedPnl(startOfWeek);

        return new PositionDashboard(
                openPositions.size(),
                totalUnrealizedPnL,
                totalUnrealizedPnLPct,
                dayPnL,
                weekPnL,
                summaries
        );
    }

    public PositionSummary toSummary(Position position) {
        BigDecimal latestPrice = marketDataService.getLatestPrice(position.getSymbol());
        BigDecimal currentPrice = latestPrice != null ? latestPrice : position.getEntryPrice();

        double unrealizedPnL    = pnlCalculator.unrealizedPnl(position, currentPrice).doubleValue();
        double unrealizedPnLPct = pnlCalculator.unrealizedPnlPct(position, currentPrice);

        Duration holdTime = position.getOpenedAt() != null
                ? Duration.between(position.getOpenedAt(), Instant.now())
                : Duration.ZERO;

        return new PositionSummary(
                position.getId(),
                position.getSymbol(),
                position.getSide() != null ? position.getSide().name() : "UNKNOWN",
                position.getQuantity().doubleValue(),
                position.getEntryPrice().doubleValue(),
                currentPrice.doubleValue(),
                unrealizedPnL,
                unrealizedPnLPct,
                position.getStopLoss() != null ? position.getStopLoss().doubleValue() : 0.0,
                position.getTakeProfit() != null ? position.getTakeProfit().doubleValue() : 0.0,
                position.getStrategy(),
                holdTime
        );
    }

    private double sumRealizedPnl(Instant since) {
        return positionRepository.findByStatusAndClosedAtAfter(Position.PositionStatus.CLOSED, since).stream()
                .filter(p -> p.getRealizedPnl() != null)
                .mapToDouble(p -> p.getRealizedPnl().doubleValue())
                .sum();
    }
}
