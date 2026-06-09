package com.tradie.executor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.executor.dto.ModifyStopRequest;
import com.tradie.executor.dto.ModifyTargetRequest;
import com.tradie.executor.dto.PositionCloseRequest;
import com.tradie.executor.dto.PositionDashboard;
import com.tradie.executor.dto.PositionSummary;
import com.tradie.executor.position.PositionDashboardService;
import com.tradie.executor.position.PositionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PositionController.class)
class PositionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PositionService positionService;
    @MockBean private PositionDashboardService dashboardService;

    @Test
    void getOpenPositions_returnsSummaryList() throws Exception {
        PositionSummary summary = buildSummary();
        when(positionService.getOpenPositions()).thenReturn(List.of());
        when(dashboardService.toSummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getDashboard_returnsDashboard() throws Exception {
        PositionDashboard dashboard = new PositionDashboard(1, 50.0, 1.5, 50.0, 100.0, List.of());
        when(dashboardService.getDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/api/positions/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openPositionCount").value(1))
                .andExpect(jsonPath("$.totalUnrealizedPnL").value(50.0));
    }

    @Test
    void getPosition_existingId_returnsSummary() throws Exception {
        UUID id = UUID.randomUUID();
        com.tradie.common.entity.Position pos = new com.tradie.common.entity.Position();
        when(positionService.getPosition(id)).thenReturn(Optional.of(pos));
        when(dashboardService.toSummary(pos)).thenReturn(buildSummary());

        mockMvc.perform(get("/api/positions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void getPosition_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(positionService.getPosition(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/positions/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void closePosition_success_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(positionService).closePosition(eq(id), any());

        mockMvc.perform(post("/api/positions/" + id + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PositionCloseRequest("test"))))
                .andExpect(status().isOk());

        verify(positionService).closePosition(id, "test");
    }

    @Test
    void closePosition_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("not found")).when(positionService).closePosition(eq(id), any());

        mockMvc.perform(post("/api/positions/" + id + "/close"))
                .andExpect(status().isNotFound());
    }

    @Test
    void closePosition_illegalState_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("Not connected")).when(positionService).closePosition(eq(id), any());

        mockMvc.perform(post("/api/positions/" + id + "/close"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Not connected"));
    }

    @Test
    void modifyStop_updatesStop() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(positionService).modifyStop(eq(id), any());

        mockMvc.perform(put("/api/positions/" + id + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ModifyStopRequest(BigDecimal.valueOf(142.0)))))
                .andExpect(status().isOk());

        verify(positionService).modifyStop(eq(id), eq(BigDecimal.valueOf(142.0)));
    }

    @Test
    void modifyStop_missingPrice_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/positions/" + id + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stopPrice\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifyTarget_missingPrice_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/positions/" + id + "/target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPrice\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modifyTarget_updatesTarget() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(positionService).modifyTarget(eq(id), any());

        mockMvc.perform(put("/api/positions/" + id + "/target")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ModifyTargetRequest(BigDecimal.valueOf(170.0)))))
                .andExpect(status().isOk());

        verify(positionService).modifyTarget(eq(id), eq(BigDecimal.valueOf(170.0)));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private PositionSummary buildSummary() {
        return new PositionSummary(
                UUID.randomUUID(), "AAPL", "BUY", 10.0,
                150.0, 155.0, 50.0, 3.33,
                140.0, 165.0, "FVG", Duration.ofHours(2)
        );
    }
}
