package com.tradie.strategy.dto;

import java.math.BigDecimal;
import java.util.Optional;

public record RuleResult(boolean passed, String reason, Optional<BigDecimal> sizeAdjustmentFactor) {

    public static RuleResult pass() {
        return new RuleResult(true, null, Optional.empty());
    }

    public static RuleResult passWithAdjustment(BigDecimal factor) {
        return new RuleResult(true, null, Optional.of(factor));
    }

    public static RuleResult fail(String reason) {
        return new RuleResult(false, reason, Optional.empty());
    }
}
