package com.tradie.strategy.controller;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.dto.PortfolioHeatData;
import com.tradie.strategy.service.DailyMetricsService;
import com.tradie.strategy.service.PortfolioHeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskDashboardController.class)
class RiskDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioHeatService portfolioHeatService;

    @MockBean
    private DailyMetricsService dailyMetricsService;

    @MockBean
    private PositionRepository positionRepository;

    @Test
    void getDashboard_returnsCorrectResponse() throws Exception {
        PortfolioHeatData heatData = new PortfolioHeatData(
                3.5,
                List.of(new PortfolioHeatData.PositionHeatEntry("AAPL", 200.0, 2.0),
                        new PortfolioHeatData.PositionHeatEntry("MSFT", 150.0, 1.5)),
                Instant.now()
        );

        when(portfolioHeatService.calculateAndStore()).thenReturn(heatData);
        when(dailyMetricsService.getDailyPnl()).thenReturn(BigDecimal.valueOf(250));
        when(dailyMetricsService.getDailyTradeCount()).thenReturn(3L);
        when(dailyMetricsService.getConsecutiveLosses()).thenReturn(0L);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(2L);

        mockMvc.perform(get("/api/risk/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioHeatPct").value(3.5))
                .andExpect(jsonPath("$.riskCapacityRemainingPct").value(2.5))
                .andExpect(jsonPath("$.openPositionCount").value(2))
                .andExpect(jsonPath("$.dailyTradeCount").value(3))
                .andExpect(jsonPath("$.losingStreak").value(0))
                .andExpect(jsonPath("$.openPositions").isArray())
                .andExpect(jsonPath("$.openPositions.length()").value(2));
    }

    @Test
    void getDashboard_noOpenPositions_returnsZeroHeat() throws Exception {
        PortfolioHeatData heatData = new PortfolioHeatData(0.0, List.of(), Instant.now());

        when(portfolioHeatService.calculateAndStore()).thenReturn(heatData);
        when(dailyMetricsService.getDailyPnl()).thenReturn(BigDecimal.ZERO);
        when(dailyMetricsService.getDailyTradeCount()).thenReturn(0L);
        when(dailyMetricsService.getConsecutiveLosses()).thenReturn(0L);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        mockMvc.perform(get("/api/risk/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioHeatPct").value(0.0))
                .andExpect(jsonPath("$.riskCapacityRemainingPct").value(6.0))
                .andExpect(jsonPath("$.openPositionCount").value(0));
    }

    @Test
    void getDashboard_losingStreak_included() throws Exception {
        PortfolioHeatData heatData = new PortfolioHeatData(1.0, List.of(), Instant.now());

        when(portfolioHeatService.calculateAndStore()).thenReturn(heatData);
        when(dailyMetricsService.getDailyPnl()).thenReturn(BigDecimal.valueOf(-150));
        when(dailyMetricsService.getDailyTradeCount()).thenReturn(4L);
        when(dailyMetricsService.getConsecutiveLosses()).thenReturn(3L);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        mockMvc.perform(get("/api/risk/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.losingStreak").value(3))
                .andExpect(jsonPath("$.dailyPnl").value(-150));
    }
}
