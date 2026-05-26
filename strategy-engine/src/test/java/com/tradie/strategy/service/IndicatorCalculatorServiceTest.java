package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradie.strategy.dto.IndicatorSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.BaseBarSeries;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IndicatorCalculatorServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IndicatorCalculatorService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new IndicatorCalculatorService(redisTemplate, objectMapper,
                new SimpleMeterRegistry());

        ReflectionTestUtils.setField(service, "rsiPeriod", 14);
        ReflectionTestUtils.setField(service, "rsiOverbought", 70.0);
        ReflectionTestUtils.setField(service, "rsiOversold", 30.0);
        ReflectionTestUtils.setField(service, "macdFast", 12);
        ReflectionTestUtils.setField(service, "macdSlow", 26);
        ReflectionTestUtils.setField(service, "macdSignalPeriod", 9);
        ReflectionTestUtils.setField(service, "adxPeriod", 14);
        ReflectionTestUtils.setField(service, "adxTrendingThreshold", 25.0);
        ReflectionTestUtils.setField(service, "atrPeriod", 14);
        ReflectionTestUtils.setField(service, "bbPeriod", 20);
        ReflectionTestUtils.setField(service, "bbStdDev", 2.0);
        ReflectionTestUtils.setField(service, "volumeSmaPeriod", 20);
    }

    private BaseBarSeries buildRisingPriceSeries(int barCount) {
        BaseBarSeries series = new BaseBarSeries("TEST");
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < barCount; i++) {
            double price = 100.0 + i;
            series.addBar(Duration.ofHours(1), now.minusHours(barCount - i),
                    price - 1, price + 1, price - 1.5, price, 1000.0);
        }
        return series;
    }

    // Oscillating series: prices alternate up/down to produce neutral RSI (~50).
    private BaseBarSeries buildOscillatingSeries(int barCount) {
        BaseBarSeries series = new BaseBarSeries("OSCILLATING");
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < barCount; i++) {
            double price = 100.0 + (i % 2 == 0 ? 1.0 : -1.0);
            series.addBar(Duration.ofHours(1), now.minusHours(barCount - i),
                    price - 0.5, price + 0.5, price - 1.0, price, 1000.0);
        }
        return series;
    }

    @Test
    void calculateLatest_risingPrices_rsiAbove50() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertNotNull(snapshot);
        assertEquals("AAPL", snapshot.symbol());
        assertEquals("1H", snapshot.timeframe());
        assertTrue(snapshot.rsi() > 50, "RSI should be > 50 for rising prices, was: " + snapshot.rsi());
    }

    @Test
    void calculateLatest_risingPrices_emaAlignmentBullish() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertEquals("BULLISH", snapshot.emaAlignment(),
                "EMA alignment should be BULLISH for sustained uptrend");
    }

    @Test
    void calculateLatest_rsiSignal_neutral() {
        BaseBarSeries series = buildOscillatingSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "OSCILLATING", "NASDAQ", "1H");

        assertEquals("NEUTRAL", snapshot.rsiSignal());
    }

    @Test
    void calculateLatest_insufficientBars_returnsEmptySnapshot() {
        BaseBarSeries series = buildRisingPriceSeries(5);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertEquals(0.0, snapshot.rsi());
        assertEquals(0.0, snapshot.adx());
        assertEquals("MIXED", snapshot.emaAlignment());
    }

    @Test
    void calculateLatest_bollingerBandsOrdered_upperAboveLower() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertTrue(snapshot.bbUpper() > snapshot.bbLower(),
                "BB upper should be above BB lower");
        assertTrue(snapshot.bbMiddle() > snapshot.bbLower(),
                "BB middle should be above BB lower");
        assertTrue(snapshot.bbUpper() > snapshot.bbMiddle(),
                "BB upper should be above BB middle");
        assertTrue(snapshot.bbWidth() > 0, "BB width should be positive");
    }

    @Test
    void calculateLatest_atrPositive() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertTrue(snapshot.atr() > 0, "ATR should be positive");
    }

    @Test
    void calculateLatest_usesRedisCache_onCacheHit() throws Exception {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        BaseBarSeries series = buildRisingPriceSeries(60);
        IndicatorSnapshot cached = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");
        String json = om.writeValueAsString(cached);
        when(valueOps.get("indicators:AAPL:NASDAQ:1H")).thenReturn(json);

        IndicatorSnapshot result = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertEquals(cached.close(), result.close(), 0.001);
    }

    @Test
    void calculateHistory_returnsCorrectNumberOfSnapshots() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        List<IndicatorSnapshot> history = service.calculateHistory(series, "AAPL", "1H", 10);

        assertEquals(10, history.size());
    }

    @Test
    void calculateLatest_volumeRatio_computedCorrectly() {
        BaseBarSeries series = buildRisingPriceSeries(60);

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertTrue(snapshot.volumeRatio() >= 0, "Volume ratio should be non-negative");
        assertTrue(snapshot.volumeSMA() > 0, "Volume SMA should be positive");
    }

    @Test
    void calculateLatest_priceFields_matchLastBar() {
        BaseBarSeries series = buildRisingPriceSeries(60);
        int lastIdx = series.getEndIndex();
        double expectedClose = series.getBar(lastIdx).getClosePrice().doubleValue();

        IndicatorSnapshot snapshot = service.calculateLatest(series, "AAPL", "NASDAQ", "1H");

        assertEquals(expectedClose, snapshot.close(), 0.001);
    }
}
