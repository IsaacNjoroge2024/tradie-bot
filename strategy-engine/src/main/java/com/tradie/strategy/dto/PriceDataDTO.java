package com.tradie.strategy.dto;

import java.util.List;

public record PriceDataDTO(
        List<String> timestamp,
        List<Double> open,
        List<Double> high,
        List<Double> low,
        List<Double> close,
        List<Double> volume
) {
}
