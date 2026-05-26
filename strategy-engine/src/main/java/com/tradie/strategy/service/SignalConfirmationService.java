package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.IndicatorSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

@Service
public class SignalConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(SignalConfirmationService.class);
    private static final double CONFIDENCE_BOOST_PER_INDICATOR = 10.0;

    @Value("${tradie.confirmation.enabled:true}")
    private boolean confirmationEnabled;

    @Value("${tradie.confirmation.min-confirmations:2}")
    private int minConfirmations;

    @Value("${tradie.confirmation.divergence-boost-pct:15.0}")
    private double divergenceBoostPct;

    @Value("${tradie.indicators.default-timeframe:1H}")
    private String defaultTimeframe;

    @Value("${tradie.indicators.default-exchange:NASDAQ}")
    private String defaultExchange;

    private final OHLCVDataService ohlcvDataService;
    private final IndicatorCalculatorService indicatorCalculatorService;
    private final DivergenceDetector divergenceDetector;

    public SignalConfirmationService(OHLCVDataService ohlcvDataService,
                                      IndicatorCalculatorService indicatorCalculatorService,
                                      DivergenceDetector divergenceDetector) {
        this.ohlcvDataService = ohlcvDataService;
        this.indicatorCalculatorService = indicatorCalculatorService;
        this.divergenceDetector = divergenceDetector;
    }

    public ConfirmationResult confirm(TradeSignal signal) {
        if (!confirmationEnabled) {
            return noConfirmation(signal.getConfidenceScore());
        }

        String timeframe = signal.getTimeframe() != null ? signal.getTimeframe() : defaultTimeframe;
        String exchange = signal.getExchange() != null ? signal.getExchange() : defaultExchange;

        BarSeries series;
        IndicatorSnapshot snapshot;
        try {
            series = ohlcvDataService.getBarSeries(signal.getSymbol(), exchange, timeframe);
            if (series.getBarCount() == 0) {
                log.debug("No OHLCV data for {}:{}, skipping confirmation", signal.getSymbol(), timeframe);
                return noConfirmation(signal.getConfidenceScore());
            }
            snapshot = indicatorCalculatorService.calculateLatest(series, signal.getSymbol(), exchange, timeframe);
        } catch (Exception e) {
            log.warn("Could not fetch indicator data for {}: {}", signal.getSymbol(), e.getMessage());
            return noConfirmation(signal.getConfidenceScore());
        }

        if (signal.getAction() == null) {
            log.warn("Signal action is null for {}, skipping confirmation", signal.getSymbol());
            return noConfirmation(signal.getConfidenceScore());
        }
        boolean isBuy = signal.getAction() == TradeSignal.SignalAction.BUY;
        List<String> confirming = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();

        checkRsi(snapshot, isBuy, confirming, conflicting);
        checkEma50(snapshot, isBuy, confirming, conflicting);
        checkAdx(snapshot, isBuy, confirming, conflicting);
        checkMacd(snapshot, isBuy, confirming, conflicting);

        int total = 4;
        int confirmationCount = confirming.size();

        double baseConfidence = signal.getConfidenceScore() != null ? signal.getConfidenceScore() : 50.0;
        double adjustedConfidence = baseConfidence + (confirmationCount * CONFIDENCE_BOOST_PER_INDICATOR)
                - (conflicting.size() * CONFIDENCE_BOOST_PER_INDICATOR);

        // Divergence boost
        try {
            DivergenceDetector.DivergenceResult divergence =
                    divergenceDetector.detect(series, signal.getSymbol(), timeframe);
            if ((isBuy && divergence.bullish()) || (!isBuy && divergence.bearish())) {
                adjustedConfidence += divergenceBoostPct;
                confirming.add("Divergence detected (+" + divergenceBoostPct + "% confidence)");
            }
        } catch (Exception e) {
            log.debug("Divergence detection failed for {}: {}", signal.getSymbol(), e.getMessage());
        }

        adjustedConfidence = Math.max(0.0, Math.min(100.0, adjustedConfidence));
        boolean confirmed = confirmationCount >= minConfirmations;

        log.info("Signal confirmation for {} {}: confirmed={}, count={}/{}, confidence={}->{}",
                signal.getAction(), signal.getSymbol(), confirmed,
                confirmationCount, total, baseConfidence, adjustedConfidence);

        return new ConfirmationResult(confirmed, confirmationCount, total,
                adjustedConfidence, confirming, conflicting);
    }

    private void checkRsi(IndicatorSnapshot snap, boolean isBuy,
                           List<String> confirming, List<String> conflicting) {
        if (isBuy) {
            if (snap.rsi() < 70) {
                confirming.add("RSI not overbought (" + String.format("%.1f", snap.rsi()) + ")");
            } else {
                conflicting.add("RSI overbought (" + String.format("%.1f", snap.rsi()) + ")");
            }
        } else {
            if (snap.rsi() > 30) {
                confirming.add("RSI not oversold (" + String.format("%.1f", snap.rsi()) + ")");
            } else {
                conflicting.add("RSI oversold (" + String.format("%.1f", snap.rsi()) + ")");
            }
        }
    }

    private void checkEma50(IndicatorSnapshot snap, boolean isBuy,
                             List<String> confirming, List<String> conflicting) {
        if (snap.ema50() == 0) return;
        if (isBuy) {
            if (snap.close() > snap.ema50()) {
                confirming.add("Price above EMA50");
            } else {
                conflicting.add("Price below EMA50");
            }
        } else {
            if (snap.close() < snap.ema50()) {
                confirming.add("Price below EMA50");
            } else {
                conflicting.add("Price above EMA50");
            }
        }
    }

    private void checkAdx(IndicatorSnapshot snap, boolean isBuy,
                           List<String> confirming, List<String> conflicting) {
        if (snap.adx() > 20) {
            confirming.add("ADX trending (" + String.format("%.1f", snap.adx()) + ")");
        } else {
            conflicting.add("ADX weak trend (" + String.format("%.1f", snap.adx()) + ")");
        }
    }

    private void checkMacd(IndicatorSnapshot snap, boolean isBuy,
                            List<String> confirming, List<String> conflicting) {
        if (isBuy) {
            if (snap.macd() > snap.macdSignal()) {
                confirming.add("MACD above signal");
            } else {
                conflicting.add("MACD below signal");
            }
        } else {
            if (snap.macd() < snap.macdSignal()) {
                confirming.add("MACD below signal");
            } else {
                conflicting.add("MACD above signal");
            }
        }
    }

    private ConfirmationResult noConfirmation(Double confidenceScore) {
        double base = confidenceScore != null ? confidenceScore : 50.0;
        return new ConfirmationResult(true, 0, 0, base, List.of(), List.of());
    }
}
