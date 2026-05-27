package com.tradie.executor.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-memory record that maps a bracket order group submitted to IBKR.
 * All three IBKR order IDs (parent, TP, SL) are registered in OrderStatusTracker
 * as keys pointing to the same OrderGroup so any status update can look up the full context.
 */
public record OrderGroup(
        UUID signalId,
        String symbol,
        String exchange,
        String assetClass,
        String side,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        String strategy,
        int parentIbOrderId,
        int takeProfitIbOrderId,
        int stopLossIbOrderId,
        UUID parentDbId,
        UUID takeProfitDbId,
        UUID stopLossDbId,
        Instant submittedAt
) {}
