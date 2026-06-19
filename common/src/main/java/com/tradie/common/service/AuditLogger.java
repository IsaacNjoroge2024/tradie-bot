package com.tradie.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.AuditLog;
import com.tradie.common.entity.AuditLogId;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final String SYSTEM_USER = "SYSTEM";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 100;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${tradie.audit.enabled:true}")
    private boolean auditEnabled;

    public AuditLogger(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void log(String service, String action, String entityType,
                    UUID entityId, Map<String, Object> details) {
        persistLog(service, action, entityType, entityId, details);
    }

    @Async
    public void logSignalReceived(String service, TradeSignal signal) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", signal.getSymbol());
        details.put("action", signal.getAction() != null ? signal.getAction().name() : null);
        details.put("strategy", signal.getStrategy());
        details.put("price", signal.getPrice());
        details.put("source", signal.getSource() != null ? signal.getSource().name() : null);
        persistLog(service, "SIGNAL_RECEIVED", "SIGNAL", signal.getId(), details);
    }

    @Async
    public void logSignalValidated(String service, TradeSignal signal) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", signal.getSymbol());
        details.put("action", signal.getAction() != null ? signal.getAction().name() : null);
        details.put("strategy", signal.getStrategy());
        details.put("confidenceScore", signal.getConfidenceScore());
        persistLog(service, "SIGNAL_VALIDATED", "SIGNAL", signal.getId(), details);
    }

    @Async
    public void logSignalRejected(String service, TradeSignal signal, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", signal.getSymbol());
        details.put("action", signal.getAction() != null ? signal.getAction().name() : null);
        details.put("reason", reason);
        persistLog(service, "SIGNAL_REJECTED", "SIGNAL", signal.getId(), details);
    }

    @Async
    public void logOrderSubmitted(String service, Order order) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", order.getSymbol());
        details.put("side", order.getSide() != null ? order.getSide().name() : null);
        details.put("quantity", order.getQuantity());
        details.put("limitPrice", order.getLimitPrice());
        details.put("ibOrderId", order.getIbOrderId());
        details.put("isBracketParent", order.getIsBracketParent());
        persistLog(service, "ORDER_SUBMITTED", "ORDER", order.getId(), details);
    }

    @Async
    public void logOrderFilled(String service, Order order, double fillPrice) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", order.getSymbol());
        details.put("side", order.getSide() != null ? order.getSide().name() : null);
        details.put("quantity", order.getQuantity());
        details.put("fillPrice", fillPrice);
        details.put("ibOrderId", order.getIbOrderId());
        persistLog(service, "ORDER_FILLED", "ORDER", order.getId(), details);
    }

    @Async
    public void logPositionOpened(String service, Position position) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", position.getSymbol());
        details.put("side", position.getSide() != null ? position.getSide().name() : null);
        details.put("quantity", position.getQuantity());
        details.put("entryPrice", position.getEntryPrice());
        details.put("strategy", position.getStrategy());
        details.put("stopLoss", position.getStopLoss());
        details.put("takeProfit", position.getTakeProfit());
        persistLog(service, "POSITION_OPENED", "POSITION", position.getId(), details);
    }

    @Async
    public void logCloseRequested(String service, Position position, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", position.getSymbol());
        details.put("side", position.getSide() != null ? position.getSide().name() : null);
        details.put("quantity", position.getQuantity());
        details.put("entryPrice", position.getEntryPrice());
        details.put("reason", reason);
        persistLog(service, "CLOSE_REQUESTED", "POSITION", position.getId(), details);
    }

    @Async
    public void logPositionClosed(String service, Position position, double pnl) {
        Map<String, Object> details = new HashMap<>();
        details.put("symbol", position.getSymbol());
        details.put("side", position.getSide() != null ? position.getSide().name() : null);
        details.put("quantity", position.getQuantity());
        details.put("entryPrice", position.getEntryPrice());
        details.put("exitPrice", position.getExitPrice());
        details.put("realizedPnl", pnl);
        details.put("strategy", position.getStrategy());
        persistLog(service, "POSITION_CLOSED", "POSITION", position.getId(), details);
    }

    @Async
    public void logRiskEvent(String service, String event, Map<String, Object> details) {
        persistLog(service, event, "RISK", null, details);
    }

    private void persistLog(String service, String action, String entityType,
                            UUID entityId, Map<String, Object> details) {
        if (!auditEnabled) return;

        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.error("Failed to serialize audit log details: service={} action={}: {}", service, action, e.getMessage());
            return;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                AuditLog entry = new AuditLog();
                entry.setId(new AuditLogId(UUID.randomUUID(), Instant.now()));
                entry.setService(service);
                entry.setAction(action);
                entry.setEntityType(entityType);
                entry.setEntityId(entityId);
                entry.setDetails(detailsJson);
                entry.setUserId(SYSTEM_USER);
                auditLogRepository.save(entry);
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("Audit log persist attempt {}/{} failed: {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_BASE_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Failed to persist audit log after {} attempts: service={} action={} entityId={}: {}",
                MAX_RETRY_ATTEMPTS, service, action, entityId, lastException.getMessage());
    }
}
