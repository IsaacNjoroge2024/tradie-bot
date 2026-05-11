package com.tradie.strategy.controller;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.dto.PortfolioHeatData;
import com.tradie.strategy.dto.RiskDashboardResponse;
import com.tradie.strategy.service.DailyMetricsService;
import com.tradie.strategy.service.PortfolioHeatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/risk")
public class RiskDashboardController {

    @Value("${tradie.portfolio.max-heat-pct:6.0}")
    private double maxHeatPct;

    private final PortfolioHeatService portfolioHeatService;
    private final DailyMetricsService dailyMetricsService;
    private final PositionRepository positionRepository;

    public RiskDashboardController(PortfolioHeatService portfolioHeatService,
                                    DailyMetricsService dailyMetricsService,
                                    PositionRepository positionRepository) {
        this.portfolioHeatService = portfolioHeatService;
        this.dailyMetricsService = dailyMetricsService;
        this.positionRepository = positionRepository;
    }

    @GetMapping("/dashboard")
    public RiskDashboardResponse getDashboard() {
        PortfolioHeatData heatData = portfolioHeatService.getHeatData();
        BigDecimal dailyPnl = dailyMetricsService.getDailyPnl();
        long dailyTradeCount = dailyMetricsService.getDailyTradeCount();
        long losingStreak = dailyMetricsService.getConsecutiveLosses();
        int openPositionCount = (int) positionRepository.countByStatus(Position.PositionStatus.OPEN);

        double riskCapacityRemaining = Math.max(0.0, maxHeatPct - heatData.totalHeatPct());

        return new RiskDashboardResponse(
                heatData.totalHeatPct(),
                riskCapacityRemaining,
                heatData.positions(),
                openPositionCount,
                dailyPnl,
                dailyTradeCount,
                losingStreak
        );
    }
}
