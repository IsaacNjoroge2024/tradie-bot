package com.tradie.executor.dto;

import java.time.Instant;

public record AccountData(
        String accountId,
        double netLiquidation,
        double totalCashValue,
        double buyingPower,
        double availableFunds,
        Instant updatedAt
) {}
