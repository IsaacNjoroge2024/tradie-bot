package com.tradie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record TradingViewSignal(
        @NotBlank(message = "Symbol is required")
        @Size(max = 20, message = "Symbol must be 20 characters or less")
        String symbol,

        @NotBlank(message = "Action is required")
        @Pattern(regexp = "BUY|SELL", message = "Action must be BUY or SELL")
        String action,

        @NotBlank(message = "Strategy is required")
        @Size(max = 50, message = "Strategy must be 50 characters or less")
        String strategy,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        Double price,

        @JsonProperty("stop_loss")
        @Positive(message = "Stop loss must be positive")
        Double stopLoss,

        @JsonProperty("take_profit")
        @Positive(message = "Take profit must be positive")
        Double takeProfit,

        @JsonProperty(value = "auth_token", access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "Auth token is required")
        String authToken,

        String exchange,
        String timeframe,

        @Min(0) @Max(100)
        Double confidence) {

    public TradingViewSignal {
        exchange = (exchange == null || exchange.isBlank()) ? "SMART" : exchange.trim();
        timeframe = (timeframe == null || timeframe.isBlank()) ? "15m" : timeframe.trim();
    }
}
