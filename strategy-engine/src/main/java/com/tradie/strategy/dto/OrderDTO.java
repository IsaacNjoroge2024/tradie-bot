package com.tradie.strategy.dto;

import com.tradie.common.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        Instant validUntil
) {}
