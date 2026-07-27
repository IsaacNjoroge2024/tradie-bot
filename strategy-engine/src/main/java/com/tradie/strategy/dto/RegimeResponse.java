package com.tradie.strategy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record RegimeResponse(
        String symbol,
        String timeframe,
        String regime,
        double probability,
        @JsonProperty("duration_bars") int durationBars,
        @JsonProperty("all_probabilities") Map<String, Double> allProbabilities,
        RegimeRecommendationDTO recommendation
) {
}
