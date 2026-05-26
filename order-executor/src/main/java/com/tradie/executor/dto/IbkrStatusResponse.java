package com.tradie.executor.dto;

import java.time.Instant;

public record IbkrStatusResponse(
        boolean connected,
        Instant connectionTime,
        Instant lastHeartbeat,
        String accountId,
        double buyingPower,
        int nextOrderId,
        int openPositions
) {}
