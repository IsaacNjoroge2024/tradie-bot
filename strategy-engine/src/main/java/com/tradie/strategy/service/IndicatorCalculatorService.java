package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.strategy.dto.IndicatorSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorCalculatorService.class);
    private static final String REDIS_KEY_PREFIX = "indicators:";
    private static final int MIN_BARS_REQUIRED = 30;

    @Value("${tradie.indicators.rsi-period:14}")
    private int rsiPeriod;

    @Value("${tradie.indicators.rsi-overbought:70.0}")
    private double rsiOverbought;

    @Value("${tradie.indicators.rsi-oversold:30.0}")
    private double rsiOversold;

    @Value("${tradie.indicators.macd-fast:12}")
    private int macdFast;

    @Value("${tradie.indicators.macd-slow:26}")
    private int macdSlow;

    @Value("${tradie.indicators.macd-signal:9}")
    private int macdSignalPeriod;

    @Value("${tradie.indicators.adx-period:14}")
    private int adxPeriod;

    @Value("${tradie.indicators.adx-trending-threshold:25.0}")
    private double adxTrendingThreshold;

    @Value("${tradie.indicators.atr-period:14}")
    private int atrPeriod;

    @Value("${tradie.indicators.bb-period:20}")
    private int bbPeriod;

    @Value("${tradie.indicators.bb-std-dev:2.0}")
    private double bbStdDev;

    @Value("${tradie.indicators.volume-sma-period:20}")
    private int volumeSmaPeriod;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Timer calculationTimer;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public IndicatorCalculatorService(StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.calculationTimer = Timer.builder("tradie.indicators.calculation.time")
                .description("Time to calculate indicators")
                .register(meterRegistry);
        this.cacheHitCounter = Counter.builder("tradie.indicators.cache.hit")
                .description("Indicator cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("tradie.indicators.cache.miss")
                .description("Indicator cache misses")
                .register(meterRegistry);
    }

    public IndicatorSnapshot calculateLatest(BarSeries series, String symbol,
                                              String exchange, String timeframe) {
        String redisKey = REDIS_KEY_PREFIX + symbol + ":" + exchange + ":" + timeframe;

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                cacheHitCounter.increment();
                return objectMapper.readValue(cached, IndicatorSnapshot.class);
            }
        } catch (Exception e) {
            log.debug("Could not read indicator cache for {}: {}", redisKey, e.getMessage());
        }

        cacheMissCounter.increment();
        long startMs = System.currentTimeMillis();
        IndicatorSnapshot snapshot = calculationTimer.record(
                () -> doCalculate(series, symbol, timeframe, series.getEndIndex()));
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed > 100) {
            log.warn("Slow indicator calculation for {}:{} took {}ms", symbol, timeframe, elapsed);
        }

        cacheToRedis(redisKey, timeframe, snapshot);
        return snapshot;
    }

    public List<IndicatorSnapshot> calculateHistory(BarSeries series, String symbol,
                                                     String timeframe, int periods) {
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(series.getBeginIndex(), endIndex - periods + 1);
        List<IndicatorSnapshot> history = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            history.add(doCalculate(series, symbol, timeframe, i));
        }
        return history;
    }

    private IndicatorSnapshot doCalculate(BarSeries series, String symbol,
                                           String timeframe, int index) {
        if (series.getBarCount() < MIN_BARS_REQUIRED || index < 0) {
            log.warn("Insufficient bars ({}) for indicator calculation on {}:{}",
                    series.getBarCount(), symbol, timeframe);
            return emptySnapshot(symbol, timeframe);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // EMAs
        EMAIndicator ema9Ind = new EMAIndicator(closePrice, 9);
        EMAIndicator ema21Ind = new EMAIndicator(closePrice, 21);
        EMAIndicator ema50Ind = new EMAIndicator(closePrice, 50);
        EMAIndicator ema200Ind = new EMAIndicator(closePrice, 200);

        // RSI
        RSIIndicator rsiInd = new RSIIndicator(closePrice, rsiPeriod);

        // MACD
        MACDIndicator macdInd = new MACDIndicator(closePrice, macdFast, macdSlow);
        EMAIndicator macdSignalInd = new EMAIndicator(macdInd, macdSignalPeriod);

        // ADX
        ADXIndicator adxInd = new ADXIndicator(series, adxPeriod);
        PlusDIIndicator plusDIInd = new PlusDIIndicator(series, adxPeriod);
        MinusDIIndicator minusDIInd = new MinusDIIndicator(series, adxPeriod);

        // ATR
        ATRIndicator atrInd = new ATRIndicator(series, atrPeriod);

        // Bollinger Bands
        SMAIndicator smaForBB = new SMAIndicator(closePrice, bbPeriod);
        StandardDeviationIndicator sdInd = new StandardDeviationIndicator(closePrice, bbPeriod);
        BollingerBandsMiddleIndicator bbMiddleInd = new BollingerBandsMiddleIndicator(smaForBB);
        Num bbMultiplier = series.numOf(bbStdDev);
        BollingerBandsUpperIndicator bbUpperInd =
                new BollingerBandsUpperIndicator(bbMiddleInd, sdInd, bbMultiplier);
        BollingerBandsLowerIndicator bbLowerInd =
                new BollingerBandsLowerIndicator(bbMiddleInd, sdInd, bbMultiplier);

        // Extract values
        double close = safeDouble(closePrice, index);
        double open = series.getBar(index).getOpenPrice().doubleValue();
        double high = series.getBar(index).getHighPrice().doubleValue();
        double low = series.getBar(index).getLowPrice().doubleValue();

        double ema9Val = safeDouble(ema9Ind, index);
        double ema21Val = safeDouble(ema21Ind, index);
        double ema50Val = safeDouble(ema50Ind, index);
        double ema200Val = safeDouble(ema200Ind, index);

        double rsiVal = safeDouble(rsiInd, index);
        double macdVal = safeDouble(macdInd, index);
        double macdSignalVal = safeDouble(macdSignalInd, index);
        double macdHistogram = macdVal - macdSignalVal;

        double adxVal = safeDouble(adxInd, index);
        double plusDIVal = safeDouble(plusDIInd, index);
        double minusDIVal = safeDouble(minusDIInd, index);

        double atrVal = safeDouble(atrInd, index);
        double bbUpperVal = safeDouble(bbUpperInd, index);
        double bbMiddleVal = safeDouble(bbMiddleInd, index);
        double bbLowerVal = safeDouble(bbLowerInd, index);
        double bbWidth = bbUpperVal - bbLowerVal;

        double volumeVal = series.getBar(index).getVolume().doubleValue();
        double volumeSmaVal = computeVolumeSma(series, index, volumeSmaPeriod);
        double volumeRatio = volumeSmaVal > 0 ? volumeVal / volumeSmaVal : 0.0;

        Instant timestamp = series.getBar(index).getEndTime().toInstant();

        return new IndicatorSnapshot(
                symbol, timeframe, timestamp,
                close, open, high, low,
                ema9Val, ema21Val, ema50Val, ema200Val,
                classifyEmaAlignment(close, ema9Val, ema21Val, ema50Val),
                rsiVal, classifyRsi(rsiVal),
                macdVal, macdSignalVal, macdHistogram,
                adxVal, plusDIVal, minusDIVal,
                classifyAdx(adxVal),
                atrVal, bbUpperVal, bbMiddleVal, bbLowerVal, bbWidth,
                volumeVal, volumeSmaVal, volumeRatio
        );
    }

    private double computeVolumeSma(BarSeries series, int index, int period) {
        int startIndex = Math.max(series.getBeginIndex(), index - period + 1);
        double sum = 0.0;
        int count = 0;
        for (int i = startIndex; i <= index; i++) {
            sum += series.getBar(i).getVolume().doubleValue();
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    private void cacheToRedis(String key, String timeframe, IndicatorSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, getRedisTtl(timeframe));
        } catch (Exception e) {
            log.warn("Failed to cache indicator snapshot: {}", e.getMessage());
        }
    }

    private Duration getRedisTtl(String timeframe) {
        return switch (timeframe.toUpperCase()) {
            case "1M"  -> Duration.ofSeconds(30);
            case "5M"  -> Duration.ofSeconds(90);
            case "15M" -> Duration.ofMinutes(5);
            case "1H"  -> Duration.ofMinutes(15);
            case "4H", "1D" -> Duration.ofHours(1);
            default    -> Duration.ofMinutes(15);
        };
    }

    private double safeDouble(org.ta4j.core.Indicator<Num> indicator, int index) {
        try {
            double val = indicator.getValue(index).doubleValue();
            return Double.isNaN(val) || Double.isInfinite(val) ? 0.0 : val;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String classifyRsi(double rsi) {
        if (rsi >= rsiOverbought) return "OVERBOUGHT";
        if (rsi <= rsiOversold) return "OVERSOLD";
        return "NEUTRAL";
    }

    private String classifyAdx(double adx) {
        return adx >= adxTrendingThreshold ? "TRENDING" : "RANGING";
    }

    private String classifyEmaAlignment(double close, double ema9, double ema21, double ema50) {
        boolean bullish = close > ema9 && ema9 > ema21 && ema21 > ema50;
        boolean bearish = close < ema9 && ema9 < ema21 && ema21 < ema50;
        if (bullish) return "BULLISH";
        if (bearish) return "BEARISH";
        return "MIXED";
    }

    private IndicatorSnapshot emptySnapshot(String symbol, String timeframe) {
        return new IndicatorSnapshot(
                symbol, timeframe, Instant.now(),
                0, 0, 0, 0,
                0, 0, 0, 0, "MIXED",
                0, "NEUTRAL",
                0, 0, 0,
                0, 0, 0, "RANGING",
                0, 0, 0, 0, 0,
                0, 0, 0
        );
    }
}
