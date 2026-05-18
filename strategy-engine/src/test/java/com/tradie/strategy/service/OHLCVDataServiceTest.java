package com.tradie.strategy.service;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.OHLCVId;
import com.tradie.common.repository.OHLCVCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.ta4j.core.BarSeries;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OHLCVDataServiceTest {

    @Mock
    private OHLCVCandleRepository repository;

    private OHLCVDataService service;

    @BeforeEach
    void setUp() {
        service = new OHLCVDataService(repository);
        ReflectionTestUtils.setField(service, "defaultLookbackBars", 50);
    }

    private OHLCVCandle buildCandle(String symbol, String exchange, String timeframe,
                                     Instant time, double open, double high,
                                     double low, double close, long volume) {
        OHLCVCandle candle = new OHLCVCandle();
        candle.setId(new OHLCVId(time, symbol, exchange, timeframe));
        candle.setOpen(open);
        candle.setHigh(high);
        candle.setLow(low);
        candle.setClose(close);
        candle.setVolume(volume);
        return candle;
    }

    private List<OHLCVCandle> buildCandleList(int count, String symbol,
                                               String exchange, String timeframe) {
        List<OHLCVCandle> candles = new ArrayList<>();
        Instant base = Instant.now().minus(count, ChronoUnit.HOURS);
        for (int i = 0; i < count; i++) {
            double price = 100.0 + i;
            candles.add(buildCandle(symbol, exchange, timeframe,
                    base.plus(i, ChronoUnit.HOURS),
                    price - 1, price + 1, price - 1.5, price, 1000L));
        }
        return candles;
    }

    @Test
    void getBarSeries_withCandles_returnsPopulatedSeries() {
        List<OHLCVCandle> candles = buildCandleList(30, "AAPL", "NASDAQ", "1H");
        when(repository.findRecentBars(eq("AAPL"), eq("NASDAQ"), eq("1H"), any(Instant.class)))
                .thenReturn(candles);

        BarSeries series = service.getBarSeries("AAPL", "NASDAQ", "1H");

        assertEquals(30, series.getBarCount());
        assertEquals("AAPL", series.getName());
    }

    @Test
    void getBarSeries_noCandles_returnsEmptySeries() {
        when(repository.findRecentBars(any(), any(), any(), any())).thenReturn(List.of());

        BarSeries series = service.getBarSeries("UNKNOWN", "NYSE", "1D");

        assertEquals(0, series.getBarCount());
    }

    @Test
    void getBarSeries_cacheHit_doesNotQueryDb() {
        List<OHLCVCandle> candles = buildCandleList(10, "MSFT", "NASDAQ", "1M");
        when(repository.findRecentBars(eq("MSFT"), eq("NASDAQ"), eq("1M"), any(Instant.class)))
                .thenReturn(candles);

        service.getBarSeries("MSFT", "NASDAQ", "1M");
        service.getBarSeries("MSFT", "NASDAQ", "1M");

        // For 1M timeframe, cache TTL is 1 minute so second call hits cache
        verify(repository, times(1)).findRecentBars(any(), any(), any(), any());
    }

    @Test
    void getBarSeries_pricesInCorrectOrder() {
        List<OHLCVCandle> candles = buildCandleList(5, "AAPL", "NASDAQ", "1H");
        when(repository.findRecentBars(any(), any(), any(), any())).thenReturn(candles);

        BarSeries series = service.getBarSeries("AAPL", "NASDAQ", "1H");

        // First bar close = 100.0, last bar close = 104.0
        assertEquals(100.0, series.getFirstBar().getClosePrice().doubleValue(), 0.001);
        assertEquals(104.0, series.getLastBar().getClosePrice().doubleValue(), 0.001);
    }

    @Test
    void getBarDuration_returnsCorrectDuration() {
        assertEquals(java.time.Duration.ofMinutes(1), OHLCVDataService.getBarDuration("1M"));
        assertEquals(java.time.Duration.ofMinutes(15), OHLCVDataService.getBarDuration("15M"));
        assertEquals(java.time.Duration.ofHours(1), OHLCVDataService.getBarDuration("1H"));
        assertEquals(java.time.Duration.ofHours(4), OHLCVDataService.getBarDuration("4H"));
        assertEquals(java.time.Duration.ofDays(1), OHLCVDataService.getBarDuration("1D"));
    }
}
