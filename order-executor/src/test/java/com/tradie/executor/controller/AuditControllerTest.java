package com.tradie.executor.controller;

import com.tradie.common.entity.AuditLog;
import com.tradie.common.entity.AuditLogId;
import com.tradie.common.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuditLogRepository auditLogRepository;

    @Test
    void queryAuditLogs_noFilters_returnsPage() throws Exception {
        Page<AuditLog> page = new PageImpl<>(
                List.of(buildAuditLog("api-gateway", "SIGNAL_RECEIVED")),
                PageRequest.of(0, 50), 1);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].service").value("api-gateway"))
                .andExpect(jsonPath("$.content[0].action").value("SIGNAL_RECEIVED"));
    }

    @Test
    void queryAuditLogs_withServiceFilter_passesFilter() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit").param("service", "order-executor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void queryAuditLogs_emptyResult_returnsEmptyPage() throws Exception {
        Page<AuditLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void queryAuditLogs_negativePage_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/audit").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryAuditLogs_oversizedSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/audit").param("size", "201"))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AuditLog buildAuditLog(String service, String action) {
        AuditLog log = new AuditLog();
        log.setId(new AuditLogId(UUID.randomUUID(), Instant.now()));
        log.setService(service);
        log.setAction(action);
        log.setEntityType("SIGNAL");
        log.setUserId("SYSTEM");
        log.setDetails("{\"symbol\":\"AAPL\"}");
        return log;
    }
}
