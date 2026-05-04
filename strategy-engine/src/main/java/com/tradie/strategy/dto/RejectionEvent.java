package com.tradie.strategy.dto;

import java.time.Instant;
import java.util.UUID;

public record RejectionEvent(
        UUID signalId,
        String symbol,
        String action,
        String strategy,
        String rejectionReason,
        Instant timestamp
) {}
