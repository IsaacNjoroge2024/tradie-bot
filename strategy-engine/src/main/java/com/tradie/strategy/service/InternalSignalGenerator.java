package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates trade signals internally using ta4j indicator patterns,
 * supplementing TradingView webhook signals.
 */
@Service
public class InternalSignalGenerator {

    private static final Logger log = LoggerFactory.getLogger(InternalSignalGenerator.class);

    private final OHLCVDataService ohlcvDataService;
    private final IndicatorCalculatorService indicatorCalculatorService;

    public InternalSignalGenerator(OHLCVDataService ohlcvDataService,
                                    IndicatorCalculatorService indicatorCalculatorService) {
        this.ohlcvDataService = ohlcvDataService;
        this.indicatorCalculatorService = indicatorCalculatorService;
    }

    public List<InternalSignal> detectSignals(String symbol, String exchange, String timeframe) {
        List<InternalSignal> signals = new ArrayList<>();

        BarSeries series;
        try {
            series = ohlcvDataService.getBarSeries(symbol, exchange, timeframe);
        } catch (Exception e) {
            log.warn("Could not load OHLCV data for internal signal detection on {}: {}",
                    symbol, e.getMessage());
            return signals;
        }

        if (series.getBarCount() < 30) {
            return signals;
        }

        detectEmaCrossover(series, symbol, exchange, timeframe, signals);
        detectRsiReversal(series, symbol, exchange, timeframe, signals);
        detectMacdCrossover(series, symbol, exchange, timeframe, signals);

        return signals;
    }

    // EMA crossover: 9 EMA crosses 21 EMA.
    private void detectEmaCrossover(BarSeries series, String symbol, String exchange,
                                     String timeframe, List<InternalSignal> signals) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) return;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema9 = new EMAIndicator(closePrice, 9);
        EMAIndicator ema21 = new EMAIndicator(closePrice, 21);

        double ema9Curr = safeDouble(ema9, endIndex);
        double ema21Curr = safeDouble(ema21, endIndex);
        double ema9Prev = safeDouble(ema9, endIndex - 1);
        double ema21Prev = safeDouble(ema21, endIndex - 1);

        if (ema9Prev <= ema21Prev && ema9Curr > ema21Curr) {
            log.info("EMA crossover BUY detected for {}:{}", symbol, timeframe);
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.BUY, "EMA_CROSSOVER", 60.0));
        } else if (ema9Prev >= ema21Prev && ema9Curr < ema21Curr) {
            log.info("EMA crossover SELL detected for {}:{}", symbol, timeframe);
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.SELL, "EMA_CROSSOVER", 60.0));
        }
    }

    // RSI reversal: RSI crosses back above 30 (oversold exit) or below 70 (overbought exit).
    private void detectRsiReversal(BarSeries series, String symbol, String exchange,
                                    String timeframe, List<InternalSignal> signals) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) return;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        double prev = safeDouble(rsi, endIndex - 1);
        double curr = safeDouble(rsi, endIndex);

        if (prev < 30 && curr >= 30) {
            log.info("RSI reversal BUY detected for {}:{} (RSI {} → {})",
                    symbol, timeframe, String.format("%.1f", prev), String.format("%.1f", curr));
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.BUY, "RSI_REVERSAL", 55.0));
        } else if (prev > 70 && curr <= 70) {
            log.info("RSI reversal SELL detected for {}:{} (RSI {} → {})",
                    symbol, timeframe, String.format("%.1f", prev), String.format("%.1f", curr));
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.SELL, "RSI_REVERSAL", 55.0));
        }
    }

    // MACD crossover: MACD line crosses the signal line.
    private void detectMacdCrossover(BarSeries series, String symbol, String exchange,
                                      String timeframe, List<InternalSignal> signals) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) return;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);

        double macdCurr = safeDouble(macd, endIndex);
        double signalCurr = safeDouble(macdSignal, endIndex);
        double macdPrev = safeDouble(macd, endIndex - 1);
        double signalPrev = safeDouble(macdSignal, endIndex - 1);

        if (macdPrev <= signalPrev && macdCurr > signalCurr) {
            log.info("MACD crossover BUY detected for {}:{}", symbol, timeframe);
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.BUY, "MACD_CROSSOVER", 58.0));
        } else if (macdPrev >= signalPrev && macdCurr < signalCurr) {
            log.info("MACD crossover SELL detected for {}:{}", symbol, timeframe);
            signals.add(new InternalSignal(symbol, exchange, timeframe,
                    TradeSignal.SignalAction.SELL, "MACD_CROSSOVER", 58.0));
        }
    }

    private double safeDouble(org.ta4j.core.Indicator<org.ta4j.core.num.Num> indicator, int index) {
        try {
            double val = indicator.getValue(index).doubleValue();
            return Double.isNaN(val) ? 0.0 : val;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public record InternalSignal(
            String symbol,
            String exchange,
            String timeframe,
            TradeSignal.SignalAction action,
            String strategy,
            double confidence
    ) {}
}
