package com.tradie.executor.dto;

import com.tradie.common.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a validated order received from the tradie.orders Kafka topic.
 * Mirrors the OrderDTO published by the Strategy Engine.
 */
public record OrderDTO(
        UUID signalId,
        String symbol,
        String exchange,
        String assetClass,
        Order.OrderSide side,
        Order.OrderType orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        String strategy,
        Instant validUntil,
        BigDecimal riskAmount,
        double riskPercentage,
        double portfolioHeatBefore,
        double portfolioHeatAfter,
        BigDecimal expectedReward,
        double riskRewardRatio,
        String sizingMethod
) {}
