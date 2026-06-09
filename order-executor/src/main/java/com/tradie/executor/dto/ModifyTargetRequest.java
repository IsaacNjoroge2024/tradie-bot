package com.tradie.executor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ModifyTargetRequest(@NotNull @Positive BigDecimal targetPrice) {}
