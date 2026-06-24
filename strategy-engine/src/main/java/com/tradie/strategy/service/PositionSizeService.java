package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.AccountInfo;
import com.tradie.strategy.dto.PositionSizeResult;
import com.tradie.strategy.forex.CurrencyPairService;
import com.tradie.strategy.forex.ForexPipCalculator;
import com.tradie.strategy.forex.ForexPositionSizer;
import com.tradie.strategy.forex.dto.ForexPositionSize;
import com.tradie.strategy.crypto.CryptoPositionSizer;
import com.tradie.strategy.crypto.dto.CryptoPositionSize;
import com.tradie.strategy.futures.FuturesPositionSizer;
import com.tradie.strategy.futures.InsufficientMarginException;
import com.tradie.strategy.futures.dto.FuturesPositionSize;
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

    @Value("${tradie.position-sizing.risk-per-trade-pct:2.0}")
    private double riskPerTradePct;

    @Value("${tradie.portfolio.warning-heat-pct:4.0}")
    private double warningHeatPct;

    @Value("${tradie.portfolio.reduce-heat-pct:5.0}")
    private double reduceHeatPct;

    private final AccountService accountService;
    private final PortfolioHeatService portfolioHeatService;
    private final FixedFractionalCalculator fixedFractionalCalculator;
    private final KellyCriterionCalculator kellyCriterionCalculator;
    private final ATRPositionSizeCalculator atrCalculator;
    private final CorrelationAdjuster correlationAdjuster;
    private final ObjectMapper objectMapper;
    private final ForexPipCalculator forexPipCalculator;
    private final ForexPositionSizer forexPositionSizer;
    private final FuturesPositionSizer futuresPositionSizer;
    private final CryptoPositionSizer cryptoPositionSizer;

    public PositionSizeService(AccountService accountService,
                                PortfolioHeatService portfolioHeatService,
                                FixedFractionalCalculator fixedFractionalCalculator,
                                KellyCriterionCalculator kellyCriterionCalculator,
                                ATRPositionSizeCalculator atrCalculator,
                                CorrelationAdjuster correlationAdjuster,
                                ObjectMapper objectMapper,
                                ForexPipCalculator forexPipCalculator,
                                ForexPositionSizer forexPositionSizer,
                                FuturesPositionSizer futuresPositionSizer,
                                CryptoPositionSizer cryptoPositionSizer) {
        this.accountService = accountService;
        this.portfolioHeatService = portfolioHeatService;
        this.fixedFractionalCalculator = fixedFractionalCalculator;
        this.kellyCriterionCalculator = kellyCriterionCalculator;
        this.atrCalculator = atrCalculator;
        this.correlationAdjuster = correlationAdjuster;
        this.objectMapper = objectMapper;
        this.forexPipCalculator = forexPipCalculator;
        this.forexPositionSizer = forexPositionSizer;
        this.futuresPositionSizer = futuresPositionSizer;
        this.cryptoPositionSizer = cryptoPositionSizer;
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
        String assetClass = detectAssetClass(signal);

        // 1. Current portfolio heat
        double heatBefore = portfolioHeatService.getCurrentHeatPct();

        // 2. Determine sizing method
        String method = determineSizingMethod();

        // 3. Calculate base quantity
        BigDecimal quantity = calculateByMethod(method, signal, account, adjustments, assetClass);

        // Risk amount: for asset classes with custom sizers, use account × riskPerTradePct / 100.
        // For CRYPTO specifically, compute actual dollar risk as quantity × |entry - stop| so that
        // portfolio heat reflects the volatility-adjusted (lower) risk, not the full riskPerTradePct.
        BigDecimal riskAmount;
        if ("FOREX".equals(assetClass) || "FUTURES".equals(assetClass)) {
            riskAmount = account.accountValue()
                    .multiply(BigDecimal.valueOf(riskPerTradePct))
                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        } else if ("CRYPTO".equals(assetClass) && signal.getPrice() != null
                && signal.getStopLoss() != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal priceRisk = signal.getPrice().subtract(signal.getStopLoss()).abs();
            riskAmount = quantity.multiply(priceRisk).setScale(8, RoundingMode.HALF_UP);
        } else {
            riskAmount = fixedFractionalCalculator.calculateRiskAmount(account.accountValue());
        }

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
            quantity = roundForAssetClass(signal, quantity, assetClass);
            valid = quantity.compareTo(BigDecimal.ZERO) > 0;
        }
        if (!valid) {
            adjustments.add("Position size fell to zero after all adjustments");
            riskAmount = BigDecimal.ZERO;
        }

        double riskPct = valid && account.accountValue().compareTo(BigDecimal.ZERO) > 0
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
                assetClass, List.copyOf(adjustments), valid, heatBefore, heatAfter);
    }

    private String determineSizingMethod() {
        return defaultSizingMethod != null ? defaultSizingMethod.toUpperCase() : METHOD_FIXED_FRACTIONAL;
    }

    private BigDecimal calculateByMethod(String method, TradeSignal signal,
                                          AccountInfo account, List<String> adjustments,
                                          String assetClass) {
        BigDecimal entry = signal.getPrice();
        BigDecimal stopLoss = signal.getStopLoss();
        BigDecimal accountValue = account.accountValue();

        if (entry == null || stopLoss == null
                || entry.subtract(stopLoss).abs().compareTo(BigDecimal.ZERO) == 0) {
            adjustments.add("No valid stop loss - using fixed fractional fallback");
            return fallbackQuantity(entry, accountValue);
        }

        // Forex uses pip-based sizing regardless of the configured default method
        if ("FOREX".equals(assetClass)) {
            return calculateForexUnits(signal, accountValue, adjustments);
        }

        // Futures uses tick-value sizing regardless of the configured default method
        if ("FUTURES".equals(assetClass)) {
            return calculateFuturesContracts(signal, accountValue, adjustments);
        }

        // Crypto uses volatility-adjusted sizing regardless of the configured default method
        if ("CRYPTO".equals(assetClass)) {
            return calculateCryptoQuantity(signal, accountValue, adjustments);
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

    private BigDecimal calculateForexUnits(TradeSignal signal, BigDecimal accountValue,
                                            List<String> adjustments) {
        String pair;
        try {
            pair = CurrencyPairService.normalizePair(signal.getSymbol());
        } catch (IllegalArgumentException e) {
            adjustments.add("Forex sizing skipped — invalid pair symbol: " + signal.getSymbol());
            log.warn("Forex position sizing skipped due to invalid symbol: {}", signal.getSymbol());
            return BigDecimal.ZERO;
        }
        double balance = accountValue.doubleValue();
        double entryPrice = signal.getPrice().doubleValue();
        double stopLossPrice = signal.getStopLoss().doubleValue();
        double stopLossPips = forexPipCalculator.calculatePips(pair, entryPrice, stopLossPrice);

        ForexPositionSize posSize;
        try {
            posSize = forexPositionSizer.calculate(pair, balance, riskPerTradePct, entryPrice, stopLossPips);
        } catch (IllegalArgumentException e) {
            adjustments.add("Forex sizing skipped — unsupported pair: " + e.getMessage());
            log.warn("Forex position sizing not supported for {}: {}", signal.getSymbol(), e.getMessage());
            return BigDecimal.ZERO;
        }

        adjustments.add(String.format(
                "Forex pip-based sizing: %.2f lots (%,.0f units), stopLoss=%.1f pips, "
                        + "pipValue=%.2f, marginRequired=%.2f",
                posSize.lots(), posSize.units(), stopLossPips,
                posSize.pipValue(), posSize.marginRequired()));

        return BigDecimal.valueOf(posSize.units()).setScale(0, RoundingMode.FLOOR);
    }

    private BigDecimal calculateFuturesContracts(TradeSignal signal, BigDecimal accountValue,
                                                   List<String> adjustments) {
        String symbol = signal.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            adjustments.add("Futures sizing skipped — blank symbol");
            return BigDecimal.ZERO;
        }
        double balance = accountValue.doubleValue();
        double entryPrice = signal.getPrice().doubleValue();
        double stopLossPrice = signal.getStopLoss().doubleValue();

        FuturesPositionSize posSize;
        try {
            posSize = futuresPositionSizer.calculate(symbol, balance, riskPerTradePct,
                    entryPrice, stopLossPrice);
        } catch (InsufficientMarginException e) {
            adjustments.add("Futures margin validation failed: " + e.getMessage());
            log.warn("Insufficient margin for futures position {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        } catch (IllegalArgumentException | IllegalStateException e) {
            adjustments.add("Futures sizing rejected — " + e.getMessage());
            log.warn("Futures sizing rejected for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }

        adjustments.add(String.format(
                "Futures tick-based sizing: %d contract(s), notional=%.2f, ticksToStop=%.1f, marginRequired=%.2f",
                posSize.contracts(), posSize.notionalValue(), posSize.ticksToStop(), posSize.marginRequired()));

        return BigDecimal.valueOf(posSize.contracts());
    }

    private BigDecimal calculateCryptoQuantity(TradeSignal signal, BigDecimal accountValue,
                                                List<String> adjustments) {
        String symbol = signal.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            adjustments.add("Crypto sizing skipped — blank symbol");
            return BigDecimal.ZERO;
        }
        double balance = accountValue.doubleValue();
        double entryPrice = signal.getPrice().doubleValue();
        double stopLossPrice = signal.getStopLoss().doubleValue();

        CryptoPositionSize posSize;
        try {
            posSize = cryptoPositionSizer.calculate(symbol, balance, riskPerTradePct,
                    entryPrice, stopLossPrice);
        } catch (IllegalArgumentException e) {
            adjustments.add("Crypto sizing skipped — " + e.getMessage());
            log.warn("Crypto sizing rejected for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }

        adjustments.add(String.format(
                "Crypto volatility-adjusted sizing: qty=%.8f, notional=%.2f, adjustedRisk=%.2f%%",
                posSize.quantity(), posSize.notionalValue(), posSize.adjustedRiskPct()));

        return BigDecimal.valueOf(posSize.quantity()).setScale(8, RoundingMode.HALF_UP);
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

    private BigDecimal roundForAssetClass(TradeSignal signal, BigDecimal quantity, String assetClass) {
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
            if (upper.contains("CRYPTO") || upper.contains("COINBASE") || upper.contains("BINANCE")
                    || upper.equals("PAXOS")) {
                return "CRYPTO";
            }
            if (upper.contains("FOREX") || upper.contains("FX") || upper.contains("IDEALPRO")) {
                return "FOREX";
            }
            if (upper.contains("FUTURES") || upper.contains("CME") || upper.contains("GLOBEX")
                    || upper.contains("NYMEX") || upper.contains("COMEX")) {
                return "FUTURES";
            }
        }
        if (symbol != null && !symbol.isBlank()) {
            String sym = symbol.toUpperCase();
            if (sym.equals("BTC") || sym.equals("ETH") || sym.equals("LTC") || sym.equals("BCH")) {
                return "CRYPTO";
            }
            try {
                String normalized = CurrencyPairService.normalizePair(symbol);
                if (symbol.contains("/") || normalized.matches("^[A-Z]{6}$")) {
                    return "FOREX";
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid symbol format; fall through to default asset class
            }
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
