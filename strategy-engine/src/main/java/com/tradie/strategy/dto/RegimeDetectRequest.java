package com.tradie.strategy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegimeDetectRequest(
        String symbol,
        String timeframe,
        @JsonProperty("price_data") PriceDataDTO priceData
) {
}
