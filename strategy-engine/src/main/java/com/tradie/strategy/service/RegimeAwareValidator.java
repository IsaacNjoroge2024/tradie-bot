package com.tradie.strategy.service;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.RegimeClient;
import com.tradie.strategy.dto.RegimeRecommendationDTO;
import com.tradie.strategy.dto.RegimeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adapts signal validation to the current market regime (Ticket 24): rejects
 * signals whose strategy the ML Service's regime recommendation explicitly
 * advises against, and surfaces a position-size multiplier for regimes that
 * warrant reduced (or normal) sizing.
 */
@Service
public class RegimeAwareValidator {

    private final RegimeClient regimeClient;
    private final OHLCVDataService ohlcvDataService;

    @Value("${tradie.regime.min-bars:50}")
    private int minBars;

    @Value("${tradie.indicators.default-timeframe:1H}")
    private String defaultTimeframe;

    public RegimeAwareValidator(RegimeClient regimeClient, OHLCVDataService ohlcvDataService) {
        this.regimeClient = regimeClient;
        this.ohlcvDataService = ohlcvDataService;
    }

    public RegimeValidationResult validate(TradeSignal signal) {
        String timeframe = signal.getTimeframe() != null ? signal.getTimeframe() : defaultTimeframe;
        List<OHLCVCandle> candles =
                ohlcvDataService.getRecentCandles(signal.getSymbol(), signal.getExchange(), timeframe);

        if (candles.size() < minBars) {
            throw new IllegalStateException(
                    "Insufficient historical data for regime detection: need >= " + minBars
                            + " bars, got " + candles.size());
        }

        RegimeResponse response = regimeClient.detectRegime(signal.getSymbol(), timeframe, candles);
        RegimeRecommendationDTO recommendation = response.recommendation();

        // Case-insensitive: TradeSignal.strategy is stored verbatim from the TradingView
        // webhook payload (no case normalization anywhere in the ingestion pipeline), while
        // the ML Service's avoid_strategies always uses canonical uppercase names.
        boolean strategyAvoided = recommendation.avoidStrategies().stream()
                .anyMatch(avoided -> avoided.equalsIgnoreCase(signal.getStrategy()));
        if (strategyAvoided) {
            return RegimeValidationResult.rejected(response.regime(),
                    "Strategy " + signal.getStrategy() + " not recommended in " + response.regime() + " regime");
        }

        return RegimeValidationResult.approved(
                response.regime(), BigDecimal.valueOf(recommendation.positionSizeMultiplier()));
    }

    public record RegimeValidationResult(
            boolean approved, String rejectionReason, String regime, BigDecimal sizeMultiplier) {

        public static RegimeValidationResult approved(String regime, BigDecimal sizeMultiplier) {
            return new RegimeValidationResult(true, null, regime, sizeMultiplier);
        }

        public static RegimeValidationResult rejected(String regime, String reason) {
            return new RegimeValidationResult(false, reason, regime, BigDecimal.ONE);
        }
    }
}
