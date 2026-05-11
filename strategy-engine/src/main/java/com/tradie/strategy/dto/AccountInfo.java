package com.tradie.strategy.dto;

import java.math.BigDecimal;

public record AccountInfo(
        BigDecimal accountValue,
        BigDecimal buyingPower,
        BigDecimal cashBalance,
        BigDecimal unrealizedPnL
) {}
