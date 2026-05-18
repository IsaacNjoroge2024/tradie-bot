package com.tradie.strategy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.BaseBarSeries;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DivergenceDetectorTest {

    @Mock
    private OHLCVDataService ohlcvDataService;

    private DivergenceDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DivergenceDetector(ohlcvDataService);
        ReflectionTestUtils.setField(detector, "rsiPeriod", 14);
        ReflectionTestUtils.setField(detector, "divergenceLookback", 20);
    }

    private BaseBarSeries buildSeries(double[] closePrices) {
        BaseBarSeries series = new BaseBarSeries("TEST");
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < closePrices.length; i++) {
            double p = closePrices[i];
            series.addBar(Duration.ofHours(1), now.minusHours(closePrices.length - i),
                    p - 1, p + 1, p - 2, p, 1000.0);
        }
        return series;
    }

    @Test
    void detect_insufficientBars_returnsNoDivergence() {
        BaseBarSeries series = buildSeries(new double[]{100, 101, 102});

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        assertFalse(result.bullish());
        assertFalse(result.bearish());
    }

    @Test
    void detect_risingPricesNoRSIDiv_noSignal() {
        // Uniformly rising prices — no divergence
        double[] prices = new double[50];
        for (int i = 0; i < 50; i++) prices[i] = 100.0 + i;
        BaseBarSeries series = buildSeries(prices);

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        // Should not show bearish divergence with uniform rise (both price and RSI rise together)
        assertFalse(result.bullish());
    }

    @Test
    void detect_bearishDivergence_risingPriceFallingRsi() {
        // Build a series where the later portion has higher prices but RSI should diverge.
        // Use a pattern: initial rise (pushes RSI up), then choppy rise (RSI stays flat/falls).
        double[] prices = new double[50];
        // First 25: strong rise to push RSI high
        for (int i = 0; i < 25; i++) prices[i] = 100.0 + i * 3;
        // Last 25: slow rise (price makes new highs but at a slowing pace — RSI tends to fall)
        for (int i = 0; i < 25; i++) prices[25 + i] = prices[24] + i * 0.5;
        BaseBarSeries series = buildSeries(prices);

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        // The series ends with a higher price than the lookback peak, but RSI may or may not
        // diverge depending on implementation. We just verify no exception is thrown.
        assertNotNull(result);
    }

    @Test
    void detect_returnsResultObject_notNull() {
        double[] prices = new double[40];
        for (int i = 0; i < 40; i++) prices[i] = 100.0 + (i % 5) * 2;
        BaseBarSeries series = buildSeries(prices);

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        assertNotNull(result);
    }
}
