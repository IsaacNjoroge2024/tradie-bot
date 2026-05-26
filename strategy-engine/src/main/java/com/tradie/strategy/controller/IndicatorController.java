package com.tradie.strategy.controller;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.IndicatorSnapshot;
import com.tradie.strategy.service.IndicatorCalculatorService;
import com.tradie.strategy.service.OHLCVDataService;
import com.tradie.strategy.service.SignalConfirmationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;

import java.util.List;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController {

    private static final Logger log = LoggerFactory.getLogger(IndicatorController.class);

    @Value("${tradie.indicators.default-timeframe:1H}")
    private String defaultTimeframe;

    @Value("${tradie.indicators.default-exchange:NASDAQ}")
    private String defaultExchange;

    private final OHLCVDataService ohlcvDataService;
    private final IndicatorCalculatorService indicatorCalculatorService;
    private final SignalConfirmationService signalConfirmationService;

    public IndicatorController(OHLCVDataService ohlcvDataService,
                                IndicatorCalculatorService indicatorCalculatorService,
                                SignalConfirmationService signalConfirmationService) {
        this.ohlcvDataService = ohlcvDataService;
        this.indicatorCalculatorService = indicatorCalculatorService;
        this.signalConfirmationService = signalConfirmationService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<IndicatorSnapshot> getIndicators(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "") String exchange,
            @RequestParam(defaultValue = "") String timeframe) {

        String resolvedExchange = exchange.isBlank() ? defaultExchange : exchange;
        String resolvedTimeframe = timeframe.isBlank() ? defaultTimeframe : timeframe;

        BarSeries series = ohlcvDataService.getBarSeries(symbol, resolvedExchange, resolvedTimeframe);
        if (series.getBarCount() == 0) {
            log.warn("No OHLCV data for {}:{}", symbol, resolvedTimeframe);
            return ResponseEntity.noContent().build();
        }
        IndicatorSnapshot snapshot = indicatorCalculatorService.calculateLatest(
                series, symbol, resolvedExchange, resolvedTimeframe);
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<IndicatorSnapshot>> getIndicatorHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "") String exchange,
            @RequestParam(defaultValue = "") String timeframe,
            @RequestParam(defaultValue = "50") int periods) {

        if (periods <= 0) {
            return ResponseEntity.badRequest().build();
        }

        String resolvedExchange = exchange.isBlank() ? defaultExchange : exchange;
        String resolvedTimeframe = timeframe.isBlank() ? defaultTimeframe : timeframe;

        BarSeries series = ohlcvDataService.getBarSeries(symbol, resolvedExchange, resolvedTimeframe);
        if (series.getBarCount() == 0) {
            return ResponseEntity.noContent().build();
        }
        List<IndicatorSnapshot> history = indicatorCalculatorService.calculateHistory(
                series, symbol, resolvedTimeframe, periods);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{symbol}/confirm")
    public ResponseEntity<ConfirmationResult> confirmSignal(
            @PathVariable String symbol,
            @RequestParam String action,
            @RequestParam(defaultValue = "") String exchange,
            @RequestParam(defaultValue = "") String timeframe) {

        String resolvedExchange = exchange.isBlank() ? defaultExchange : exchange;
        String resolvedTimeframe = timeframe.isBlank() ? defaultTimeframe : timeframe;

        TradeSignal.SignalAction signalAction;
        try {
            signalAction = TradeSignal.SignalAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        TradeSignal synthetic = new TradeSignal();
        synthetic.setSymbol(symbol);
        synthetic.setExchange(resolvedExchange);
        synthetic.setTimeframe(resolvedTimeframe);
        synthetic.setAction(signalAction);
        synthetic.setConfidenceScore(50.0);

        ConfirmationResult result = signalConfirmationService.confirm(synthetic);
        return ResponseEntity.ok(result);
    }
}
