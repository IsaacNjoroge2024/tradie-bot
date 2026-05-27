package com.tradie.executor.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the tradie.alerts Kafka topic to notify the Alert Service
 * of significant order lifecycle changes (SUBMITTED, FILLED, CANCELLED, REJECTED,
 * TAKE_PROFIT_HIT, STOP_LOSS_HIT).
 */
public record OrderEvent(
        String type,
        UUID signalId,
        String symbol,
        String side,
        double quantity,
        double price,
        String strategy,
        String message,
        Instant timestamp
) {}
