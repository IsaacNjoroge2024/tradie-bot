package com.tradie.strategy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RegimeRecommendationDTO(
        @JsonProperty("position_size_multiplier") double positionSizeMultiplier,
        @JsonProperty("preferred_strategies") List<String> preferredStrategies,
        @JsonProperty("avoid_strategies") List<String> avoidStrategies,
        @JsonProperty("stop_loss_multiplier") double stopLossMultiplier,
        @JsonProperty("take_profit_multiplier") double takeProfitMultiplier,
        @JsonProperty("max_positions") int maxPositions,
        String notes
) {
    public RegimeRecommendationDTO {
        preferredStrategies = preferredStrategies != null ? preferredStrategies : List.of();
        avoidStrategies = avoidStrategies != null ? avoidStrategies : List.of();
    }
}
