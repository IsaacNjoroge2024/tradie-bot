package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.AccountInfo;
import com.tradie.strategy.dto.PositionSizeResult;
import com.tradie.strategy.positioning.ATRPositionSizeCalculator;
import com.tradie.strategy.positioning.FixedFractionalCalculator;
import com.tradie.strategy.positioning.KellyCriterionCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PositionSizeService {

    private static final Logger log = LoggerFactory.getLogger(PositionSizeService.class);

    private static final String METHOD_FIXED_FRACTIONAL = "FIXED_FRACTIONAL";
    private static final String METHOD_KELLY = "KELLY";
    private static final String METHOD_ATR = "ATR";

    @Value("${tradie.position-sizing.default-method:FIXED_FRACTIONAL}")
    private String defaultSizingMethod;

    @Value("${tradie.portfolio.warning-heat-pct:4.0}")
    private double warningHeatPct;

    @Value("${tradie.portfolio.reduce-heat-pct:5.0}")
    private double reduceHeatPct;

    @Value("${tradie.portfolio.max-heat-pct:6.0}")
    private double maxHeatPct;

    private final AccountService accountService;
    private final PortfolioHeatService portfolioHeatService;
    private final FixedFractionalCalculator fixedFractionalCalculator;
    private final KellyCriterionCalculator kellyCriterionCalculator;
    private final ATRPositionSizeCalculator atrCalculator;
    private final CorrelationAdjuster correlationAdjuster;
    private final ObjectMapper objectMapper;

    public PositionSizeService(AccountService accountService,
                                PortfolioHeatService portfolioHeatService,
                                FixedFractionalCalculator fixedFractionalCalculator,
                                KellyCriterionCalculator kellyCriterionCalculator,
                                ATRPositionSizeCalculator atrCalculator,
                                CorrelationAdjuster correlationAdjuster,
                                ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.portfolioHeatService = portfolioHeatService;
        this.fixedFractionalCalculator = fixedFractionalCalculator;
        this.kellyCriterionCalculator = kellyCriterionCalculator;
        this.atrCalculator = atrCalculator;
        this.correlationAdjuster = correlationAdjuster;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculates the recommended position size for a signal.
     *
     * @param signal               the trade signal to size
     * @param sizeAdjustmentFactor external adjustment factor from risk rules (e.g. losing streak)
     */
    public PositionSizeResult calculatePositionSize(TradeSignal signal,
                                                     BigDecimal sizeAdjustmentFactor) {
        AccountInfo account = accountService.getAccountInfo();
        List<String> adjustments = new ArrayList<>();

        // 1. Current portfolio heat
        double heatBefore = portfolioHeatService.getCurrentHeatPct();

        // 2. Determine sizing method
        String method = determineSizingMethod();

        // 3. Calculate base quantity
        BigDecimal quantity = calculateByMethod(method, signal, account, adjustments);
        BigDecimal riskAmount = fixedFractionalCalculator.calculateRiskAmount(account.accountValue());

        // 4. Portfolio heat adjustment
        quantity = applyHeatAdjustment(quantity, heatBefore, adjustments);

        // 5. Correlation adjustment
        quantity = correlationAdjuster.adjust(signal.getSymbol(), quantity, adjustments);

        // 6. External adjustment (losing streak, etc.)
        if (sizeAdjustmentFactor != null
                && sizeAdjustmentFactor.compareTo(BigDecimal.ONE) != 0) {
            quantity = quantity.multiply(sizeAdjustmentFactor).setScale(8, RoundingMode.HALF_UP);
            adjustments.add("Risk rule size adjustment: factor " + sizeAdjustmentFactor);
        }

        // 7. Validate minimum before lot-size floor so true zero is preserved
        boolean valid = quantity.compareTo(BigDecimal.ZERO) > 0;
        if (valid) {
            quantity = roundForAssetClass(signal, quantity);
            valid = quantity.compareTo(BigDecimal.ZERO) > 0;
        }
        if (!valid) {
            adjustments.add("Position size fell to zero after all adjustments");
        }

        double riskPct = account.accountValue().compareTo(BigDecimal.ZERO) > 0
                ? riskAmount.divide(account.accountValue(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        double heatAfter = heatBefore + riskPct;

        log.info("Position sized for {}: method={}, qty={}, riskAmount={}, riskPct={}%, "
                        + "heatBefore={}%, heatAfter={}%, adjustments={}",
                signal.getSymbol(), method, quantity, riskAmount, riskPct,
                heatBefore, heatAfter, adjustments);

        return new PositionSizeResult(
                quantity, riskAmount, riskPct, method,
                List.copyOf(adjustments), valid, heatBefore, heatAfter);
    }

    private String determineSizingMethod() {
        return defaultSizingMethod != null ? defaultSizingMethod.toUpperCase() : METHOD_FIXED_FRACTIONAL;
    }

    private BigDecimal calculateByMethod(String method, TradeSignal signal,
                                          AccountInfo account, List<String> adjustments) {
        BigDecimal entry = signal.getPrice();
        BigDecimal stopLoss = signal.getStopLoss();
        BigDecimal accountValue = account.accountValue();

        if (entry == null || stopLoss == null
                || entry.subtract(stopLoss).abs().compareTo(BigDecimal.ZERO) == 0) {
            adjustments.add("No valid stop loss - using fixed fractional fallback");
            return fallbackQuantity(entry, accountValue);
        }

        switch (method) {
            case METHOD_KELLY: {
                Optional<BigDecimal> kellyQty = kellyCriterionCalculator
                        .calculate(entry, stopLoss, accountValue, signal.getStrategy());
                if (kellyQty.isPresent()) return kellyQty.get();
                adjustments.add("Insufficient Kelly data, falling back to Fixed Fractional");
                return fixedFractionalCalculator.calculate(entry, stopLoss, accountValue);
            }
            case METHOD_ATR: {
                double atrValue = extractAtr(signal);
                Optional<BigDecimal> atrQty = atrCalculator
                        .calculate(accountValue, atrValue, signal.getStrategy());
                if (atrQty.isPresent()) return atrQty.get();
                adjustments.add("ATR not available, falling back to Fixed Fractional");
                return fixedFractionalCalculator.calculate(entry, stopLoss, accountValue);
            }
            default:
                return fixedFractionalCalculator.calculate(entry, stopLoss, accountValue);
        }
    }

    private BigDecimal applyHeatAdjustment(BigDecimal quantity, double heatBefore,
                                            List<String> adjustments) {
        if (heatBefore >= warningHeatPct && heatBefore < reduceHeatPct) {
            log.warn("Portfolio heat warning: {}%", heatBefore);
            adjustments.add(String.format("Portfolio heat warning: %.1f%%", heatBefore));
        } else if (heatBefore >= reduceHeatPct) {
            BigDecimal reduced = quantity.multiply(BigDecimal.valueOf(0.5))
                    .setScale(8, RoundingMode.HALF_UP);
            adjustments.add(String.format(
                    "High portfolio heat (%.1f%%) reduced position size by 50%%", heatBefore));
            return reduced;
        }
        return quantity;
    }

    private BigDecimal roundForAssetClass(TradeSignal signal, BigDecimal quantity) {
        String assetClass = detectAssetClass(signal);
        switch (assetClass) {
            case "FOREX":
                // Round to nearest micro lot (1K units = 1 micro lot)
                long microLots = quantity.divide(BigDecimal.valueOf(1000), 0, RoundingMode.FLOOR).longValue();
                return BigDecimal.valueOf(Math.max(1, microLots) * 1000L);
            case "CRYPTO":
                // Allow fractional, keep 8 decimal places
                return quantity.setScale(8, RoundingMode.HALF_UP);
            case "FUTURES":
                // Round to whole contracts
                long contracts = quantity.setScale(0, RoundingMode.FLOOR).longValue();
                return BigDecimal.valueOf(Math.max(1, contracts));
            default:
                // Stocks: whole shares, minimum 1
                long shares = quantity.setScale(0, RoundingMode.FLOOR).longValue();
                return BigDecimal.valueOf(Math.max(1, shares));
        }
    }

    private String detectAssetClass(TradeSignal signal) {
        String exchange = signal.getExchange();
        String symbol = signal.getSymbol();
        if (exchange != null) {
            String upper = exchange.toUpperCase();
            if (upper.contains("CRYPTO") || upper.contains("COINBASE") || upper.contains("BINANCE")) {
                return "CRYPTO";
            }
            if (upper.contains("FOREX") || upper.contains("FX")) {
                return "FOREX";
            }
            if (upper.contains("FUTURES") || upper.contains("CME") || upper.contains("GLOBEX")) {
                return "FUTURES";
            }
        }
        if (symbol != null && symbol.contains("/")) {
            return "FOREX";
        }
        return "STK";
    }

    private double extractAtr(TradeSignal signal) {
        String rawPayload = signal.getRawPayload();
        if (rawPayload == null || rawPayload.isBlank()) return 0.0;
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            JsonNode atrNode = node.get("atr");
            if (atrNode != null && !atrNode.isNull() && atrNode.isNumber()) {
                return atrNode.asDouble();
            }
        } catch (Exception e) {
            log.debug("Could not parse ATR from raw payload: {}", e.getMessage());
        }
        return 0.0;
    }

    private BigDecimal fallbackQuantity(BigDecimal entryPrice, BigDecimal accountValue) {
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal riskAmount = fixedFractionalCalculator.calculateRiskAmount(accountValue);
        BigDecimal qty = riskAmount.divide(entryPrice, 8, RoundingMode.HALF_UP);
        return qty.max(BigDecimal.ONE);
    }
}
