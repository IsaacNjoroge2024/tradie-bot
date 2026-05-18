package com.tradie.strategy.dto;

import java.time.Instant;

public record IndicatorSnapshot(
        String symbol,
        String timeframe,
        Instant timestamp,

        // Price
        double close,
        double open,
        double high,
        double low,

        // Trend
        double ema9,
        double ema21,
        double ema50,
        double ema200,
        String emaAlignment,   // BULLISH, BEARISH, MIXED

        // Momentum
        double rsi,
        String rsiSignal,      // OVERBOUGHT, OVERSOLD, NEUTRAL
        double macd,
        double macdSignal,
        double macdHistogram,

        // Trend Strength
        double adx,
        double plusDI,
        double minusDI,
        String adxSignal,      // TRENDING, RANGING

        // Volatility
        double atr,
        double bbUpper,
        double bbMiddle,
        double bbLower,
        double bbWidth,

        // Volume
        double volume,
        double volumeSMA,
        double volumeRatio
) {}
