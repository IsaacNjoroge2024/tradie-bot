package com.tradie.strategy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Service
public class DivergenceDetector {

    private static final Logger log = LoggerFactory.getLogger(DivergenceDetector.class);

    @Value("${tradie.indicators.rsi-period:14}")
    private int rsiPeriod;

    @Value("${tradie.indicators.divergence-lookback:20}")
    private int divergenceLookback;

    private final OHLCVDataService ohlcvDataService;

    public DivergenceDetector(OHLCVDataService ohlcvDataService) {
        this.ohlcvDataService = ohlcvDataService;
    }

    public DivergenceResult detect(BarSeries series, String symbol, String timeframe) {
        int endIndex = series.getEndIndex();
        if (endIndex < divergenceLookback + rsiPeriod) {
            return new DivergenceResult(false, false);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod);

        boolean bullish = detectBullishDivergence(series, rsi, endIndex);
        boolean bearish = detectBearishDivergence(series, rsi, endIndex);

        if (bullish || bearish) {
            log.debug("Divergence detected for {}: bullish={}, bearish={}", symbol, bullish, bearish);
        }
        return new DivergenceResult(bullish, bearish);
    }

    // Bullish divergence: price makes lower low, RSI makes higher low.
    private boolean detectBullishDivergence(BarSeries series, RSIIndicator rsi, int endIndex) {
        int startIndex = Math.max(0, endIndex - divergenceLookback);

        int prevLowIdx = -1;
        double prevLow = Double.MAX_VALUE;
        for (int i = startIndex; i < endIndex; i++) {
            double low = series.getBar(i).getLowPrice().doubleValue();
            if (low < prevLow) {
                prevLow = low;
                prevLowIdx = i;
            }
        }
        if (prevLowIdx == -1) return false;

        double currentLow = series.getBar(endIndex).getLowPrice().doubleValue();
        double currentRsi = safeRsi(rsi, endIndex);
        double prevRsi = safeRsi(rsi, prevLowIdx);

        return currentLow < prevLow && currentRsi > prevRsi;
    }

    // Bearish divergence: price makes higher high, RSI makes lower high.
    private boolean detectBearishDivergence(BarSeries series, RSIIndicator rsi, int endIndex) {
        int startIndex = Math.max(0, endIndex - divergenceLookback);

        int prevHighIdx = -1;
        double prevHigh = Double.NEGATIVE_INFINITY;
        for (int i = startIndex; i < endIndex; i++) {
            double high = series.getBar(i).getHighPrice().doubleValue();
            if (high > prevHigh) {
                prevHigh = high;
                prevHighIdx = i;
            }
        }
        if (prevHighIdx == -1) return false;

        double currentHigh = series.getBar(endIndex).getHighPrice().doubleValue();
        double currentRsi = safeRsi(rsi, endIndex);
        double prevRsi = safeRsi(rsi, prevHighIdx);

        return currentHigh > prevHigh && currentRsi < prevRsi;
    }

    private double safeRsi(RSIIndicator rsi, int index) {
        try {
            double val = rsi.getValue(index).doubleValue();
            return Double.isNaN(val) ? 50.0 : val;
        } catch (Exception e) {
            return 50.0;
        }
    }

    public record DivergenceResult(boolean bullish, boolean bearish) {}
}
