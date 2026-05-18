package com.tradie.strategy.dto;

import java.util.List;

public record ConfirmationResult(
        boolean confirmed,
        int confirmationCount,
        int totalIndicators,
        double adjustedConfidence,
        List<String> confirmingIndicators,
        List<String> conflictingIndicators
) {
    public ConfirmationResult {
        confirmingIndicators = confirmingIndicators == null ? List.of() : List.copyOf(confirmingIndicators);
        conflictingIndicators = conflictingIndicators == null ? List.of() : List.copyOf(conflictingIndicators);
    }
}
