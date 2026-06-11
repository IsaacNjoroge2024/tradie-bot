package com.tradie.executor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the tradie.alerts Kafka topic to notify the Alert Service
 * of significant order lifecycle changes (SUBMITTED, FILLED, CANCELLED, REJECTED,
 * TAKE_PROFIT_HIT, STOP_LOSS_HIT).
 *
 * <p>Optional fields are populated depending on event type:
 * <ul>
 *   <li>FILLED: stopLoss, takeProfit are set from the bracket order group</li>
 *   <li>TAKE_PROFIT_HIT, STOP_LOSS_HIT, POSITION_CLOSED_MANUAL: entryPrice is set for P&L display</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderEvent(
        String type,
        UUID signalId,
        String symbol,
        String side,
        double quantity,
        double price,
        String strategy,
        String message,
        Instant timestamp,
        Double stopLoss,
        Double takeProfit,
        Double entryPrice
) {}
