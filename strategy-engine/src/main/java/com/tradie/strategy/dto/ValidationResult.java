package com.tradie.strategy.dto;

import java.util.List;

public record ValidationResult(
        boolean approved,
        String rejectionReason,
        OrderDTO order,
        List<String> warnings
) {}
