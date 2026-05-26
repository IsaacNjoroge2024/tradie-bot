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
        // Uniformly rising prices — price and RSI rise together, no divergence in either direction
        double[] prices = new double[50];
        for (int i = 0; i < 50; i++) prices[i] = 100.0 + i;
        BaseBarSeries series = buildSeries(prices);

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        assertFalse(result.bullish());
        assertFalse(result.bearish());
    }

    @Test
    void detect_bearishDivergence_risingPriceFallingRsi() {
        // Phase 1 (bars 0-34): strong rise, RSI builds to near-max
        // Phase 2 (bar 35, in lookback): spike to highest price — RSI at peak (prevHighIdx)
        // Phase 3 (bars 36-44): pullback — RSI drops significantly
        // Phase 4 (bars 45-53): slow recovery
        // Phase 5 (bar 54, endIndex): new price high above bar 35, but RSI much lower
        double[] prices = new double[55];
        for (int i = 0; i < 30; i++) prices[i] = 100.0 + i * 2;       // steady rise to 158
        for (int i = 0; i < 5; i++)  prices[30 + i] = 158.0 + (i + 1) * 4; // acceleration to 178
        prices[35] = 180.0;                                              // spike — max in lookback
        double[] pullback  = {175, 170, 165, 162, 158, 155, 153, 151, 152};
        double[] recovery  = {154, 157, 160, 163, 166, 169, 172, 175, 178};
        for (int i = 0; i < 9; i++) prices[36 + i] = pullback[i];
        for (int i = 0; i < 9; i++) prices[45 + i] = recovery[i];
        prices[54] = 182.0; // new price high (182 > 180 at bar 35), RSI lower after pullback/recovery

        BaseBarSeries series = buildSeries(prices);

        DivergenceDetector.DivergenceResult result = detector.detect(series, "AAPL", "1H");

        assertTrue(result.bearish(), "Expected bearish divergence: new price high but RSI lower");
        assertFalse(result.bullish());
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
