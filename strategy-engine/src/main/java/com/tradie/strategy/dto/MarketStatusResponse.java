package com.tradie.strategy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MarketStatusResponse(
        @JsonProperty("safe_to_trade") boolean safeToTrade,
        @JsonProperty("risk_level") String riskLevel,
        List<String> reasons
) {}
