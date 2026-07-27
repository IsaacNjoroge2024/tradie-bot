package com.tradie.strategy.service;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.repository.OHLCVCandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OHLCVDataService {

    private static final Logger log = LoggerFactory.getLogger(OHLCVDataService.class);

    @Value("${tradie.indicators.default-lookback-bars:250}")
    private int defaultLookbackBars;

    private final OHLCVCandleRepository repository;
    private final Map<String, CachedBarSeries> seriesCache = new ConcurrentHashMap<>();

    public OHLCVDataService(OHLCVCandleRepository repository) {
        this.repository = repository;
    }

    public BarSeries getBarSeries(String symbol, String exchange, String timeframe) {
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        String cacheKey = symbol + ":" + exchange + ":" + normalizedTimeframe;
        CachedBarSeries cached = seriesCache.get(cacheKey);
        if (cached != null && !cached.isExpired(normalizedTimeframe)) {
            return cached.series();
        }

        Duration lookback = getLookbackDuration(normalizedTimeframe, defaultLookbackBars);
        Instant since = Instant.now().minus(lookback);
        List<OHLCVCandle> candles = repository.findRecentBars(symbol, exchange, normalizedTimeframe, since);

        BarSeries series = buildBarSeries(symbol, normalizedTimeframe, candles);
        seriesCache.put(cacheKey, new CachedBarSeries(series, Instant.now(), normalizedTimeframe));
        log.debug("Loaded {} bars for {}:{} ({})", candles.size(), symbol, normalizedTimeframe, exchange);
        return series;
    }

    // Returns raw OHLCV candles (ascending by time) rather than a ta4j BarSeries,
    // for callers that need plain bar data (e.g. building an external ML service
    // request payload) instead of indicator-ready series objects.
    public List<OHLCVCandle> getRecentCandles(String symbol, String exchange, String timeframe) {
        String normalizedTimeframe = normalizeTimeframe(timeframe);
        Duration lookback = getLookbackDuration(normalizedTimeframe, defaultLookbackBars);
        Instant since = Instant.now().minus(lookback);
        return repository.findRecentBars(symbol, exchange, normalizedTimeframe, since);
    }

    private BarSeries buildBarSeries(String symbol, String timeframe, List<OHLCVCandle> candles) {
        BaseBarSeries series = new BaseBarSeries(symbol);
        Duration barDuration = getBarDuration(timeframe);
        for (OHLCVCandle candle : candles) {
            ZonedDateTime endTime = candle.getId().getTime().atZone(ZoneOffset.UTC);
            series.addBar(barDuration, endTime,
                    candle.getOpen(), candle.getHigh(), candle.getLow(),
                    candle.getClose(), (double) candle.getVolume());
        }
        return series;
    }

    private static String normalizeTimeframe(String timeframe) {
        return timeframe == null ? "1H" : timeframe.toUpperCase();
    }

    // Returns the lookback duration needed to fetch `bars` bars for the given timeframe.
    private Duration getLookbackDuration(String timeframe, int bars) {
        return switch (normalizeTimeframe(timeframe)) {
            case "1M"  -> Duration.ofMinutes(bars);
            case "5M"  -> Duration.ofMinutes(bars * 5L);
            case "15M" -> Duration.ofMinutes(bars * 15L);
            case "1H"  -> Duration.ofHours(bars);
            case "4H"  -> Duration.ofHours(bars * 4L);
            case "1D"  -> Duration.ofDays(bars);
            default    -> Duration.ofHours(bars);
        };
    }

    static Duration getBarDuration(String timeframe) {
        return switch (normalizeTimeframe(timeframe)) {
            case "1M"  -> Duration.ofMinutes(1);
            case "5M"  -> Duration.ofMinutes(5);
            case "15M" -> Duration.ofMinutes(15);
            case "1H"  -> Duration.ofHours(1);
            case "4H"  -> Duration.ofHours(4);
            case "1D"  -> Duration.ofDays(1);
            default    -> Duration.ofHours(1);
        };
    }

    // Returns the cache TTL for BarSeries based on timeframe.
    static Duration getCacheTtl(String timeframe) {
        return switch (normalizeTimeframe(timeframe)) {
            case "1M"  -> Duration.ofMinutes(1);
            case "5M"  -> Duration.ofMinutes(2);
            case "15M" -> Duration.ofMinutes(5);
            case "1H"  -> Duration.ofMinutes(15);
            case "4H", "1D" -> Duration.ofHours(1);
            default    -> Duration.ofMinutes(15);
        };
    }

    record CachedBarSeries(BarSeries series, Instant cachedAt, String timeframe) {
        boolean isExpired(String tf) {
            return Instant.now().isAfter(cachedAt.plus(getCacheTtl(tf)));
        }
    }
}
