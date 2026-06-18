package com.tradie.executor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeJournal;
import com.tradie.executor.dto.PerformanceStats;
import com.tradie.executor.dto.TradeJournalUpdateRequest;
import com.tradie.executor.journal.JournalAnalyticsService;
import com.tradie.executor.journal.TradeJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeJournalController.class)
class TradeJournalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TradeJournalService journalService;
    @MockBean private JournalAnalyticsService analyticsService;

    @Test
    void listEntries_returnsPageOfEntries() throws Exception {
        Page<TradeJournal> page = new PageImpl<>(List.of(buildJournal()), PageRequest.of(0, 20), 1);
        when(journalService.getEntries(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/journal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getEntry_existingId_returnsEntry() throws Exception {
        UUID id = UUID.randomUUID();
        TradeJournal journal = buildJournal();
        when(journalService.getEntry(id)).thenReturn(Optional.of(journal));

        mockMvc.perform(get("/api/journal/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void getEntry_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(journalService.getEntry(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/journal/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateEntry_success_returnsUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        TradeJournal updated = buildJournal();
        updated.setNotes("Updated notes");
        when(journalService.updateEntry(eq(id), any(TradeJournalUpdateRequest.class))).thenReturn(updated);

        TradeJournalUpdateRequest req = new TradeJournalUpdateRequest(
                "Updated notes", "A", "GOOD", List.of("fvg"), null, null, null, null);

        mockMvc.perform(put("/api/journal/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Updated notes"));
    }

    @Test
    void updateEntry_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(journalService.updateEntry(eq(id), any())).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(put("/api/journal/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_returnsPerformanceStats() throws Exception {
        PerformanceStats stats = new PerformanceStats(
                10, 6, 4, 0.6, 200.0, -100.0, 2.0, -100.0, 1.5,
                Map.of(), Map.of(), Map.of(), Map.of());
        when(journalService.getByDateRange(any(), any())).thenReturn(List.of());
        when(analyticsService.calculateStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/journal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(10))
                .andExpect(jsonPath("$.winRate").value(0.6));
    }

    @Test
    void exportCsv_returnsTextCsv() throws Exception {
        when(journalService.getByDateRange(any(), any())).thenReturn(List.of(buildJournal()));
        when(journalService.exportToCsv(any())).thenReturn("id,symbol\n" + UUID.randomUUID() + ",AAPL\n");

        mockMvc.perform(get("/api/journal/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("trade_journal.csv")));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private TradeJournal buildJournal() {
        TradeJournal j = new TradeJournal();
        j.setSymbol("AAPL");
        j.setSide("BUY");
        j.setEntryTime(Instant.now().minusSeconds(3600));
        j.setEntryPrice(150.0);
        j.setQuantity(10.0);
        j.setRealizedPnl(150.0);
        j.setStrategy("FVG");
        return j;
    }
}
