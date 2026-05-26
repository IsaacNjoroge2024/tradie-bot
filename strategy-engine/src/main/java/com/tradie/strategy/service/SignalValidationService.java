package com.tradie.strategy.service;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.NewsShieldClient;
import com.tradie.strategy.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SignalValidationService {

    private static final Logger log = LoggerFactory.getLogger(SignalValidationService.class);

    private final NewsShieldClient newsShieldClient;
    private final KillZoneService killZoneService;
    private final RiskRuleService riskRuleService;
    private final PositionSizeService positionSizeService;
    private final SignalConfirmationService confirmationService;

    private final Counter receivedCounter;
    private final Counter validatedCounter;
    private final MeterRegistry meterRegistry;
    private final Timer validationTimer;

    @Value("${tradie.strategy.signal-expiry-seconds:300}")
    private int signalExpirySeconds;

    public SignalValidationService(
            NewsShieldClient newsShieldClient,
            KillZoneService killZoneService,
            RiskRuleService riskRuleService,
            PositionSizeService positionSizeService,
            SignalConfirmationService confirmationService,
            MeterRegistry meterRegistry) {
        this.newsShieldClient = newsShieldClient;
        this.killZoneService = killZoneService;
        this.riskRuleService = riskRuleService;
        this.positionSizeService = positionSizeService;
        this.confirmationService = confirmationService;
        this.meterRegistry = meterRegistry;

        this.receivedCounter  = Counter.builder("tradie.signals.received").register(meterRegistry);
        this.validatedCounter = Counter.builder("tradie.signals.validated").register(meterRegistry);
        this.validationTimer  = Timer.builder("tradie.validation.duration").register(meterRegistry);
    }

    public ValidationResult validate(TradeSignal signal) {
        return validationTimer.record(() -> doValidate(signal));
    }

    private ValidationResult doValidate(TradeSignal signal) {
        receivedCounter.increment();
        List<String> warnings = new ArrayList<>();

        // Step 1: Expiry check
        if (isExpired(signal)) {
            return reject(signal, "Signal expired");
        }

        // Step 2: News Shield
        try {
            MarketStatusResponse market = newsShieldClient.getMarketStatus(signal.getSymbol());
            if (!market.safeToTrade()) {
                return reject(signal, "News Shield: " + String.join(", ", market.reasons()));
            }
        } catch (Exception e) {
            log.warn("News Shield unavailable for symbol={}, applying fail-open fallback: {}",
                    signal.getSymbol(), e.getMessage());
            warnings.add("News Shield unavailable - proceeding with caution");
        }

        // Step 3: Kill zone timing
        KillZoneService.KillZoneResult kz = killZoneService.validate(signal);
        if (!kz.allowed()) {
            return reject(signal, kz.reason());
        }
        if (kz.warning() != null) {
            warnings.add(kz.warning());
        }

        // Step 4: Risk rules
        List<RuleResult> ruleResults = riskRuleService.validateAll(signal);
        BigDecimal sizeAdjustment = BigDecimal.ONE;
        for (RuleResult rr : ruleResults) {
            if (!rr.passed()) {
                return reject(signal, rr.reason());
            }
            if (rr.sizeAdjustmentFactor().isPresent()) {
                sizeAdjustment = sizeAdjustment.multiply(rr.sizeAdjustmentFactor().get());
                warnings.add("Position size reduced by factor " + rr.sizeAdjustmentFactor().get());
            }
        }

        // Step 5: Indicator confirmation (informational — does not reject, adjusts confidence)
        try {
            ConfirmationResult confirmation = confirmationService.confirm(signal);
            if (confirmation.totalIndicators() > 0) {
                signal.setConfidenceScore(confirmation.adjustedConfidence());
                log.debug("Indicator confirmation for {}: count={}/{}, confidence={}",
                        signal.getSymbol(), confirmation.confirmationCount(),
                        confirmation.totalIndicators(), confirmation.adjustedConfidence());
            }
            if (!confirmation.conflictingIndicators().isEmpty()) {
                warnings.add("Conflicting indicators: " +
                        String.join(", ", confirmation.conflictingIndicators()));
            }
        } catch (Exception e) {
            log.warn("Indicator confirmation failed for {}, continuing: {}",
                    signal.getSymbol(), e.getMessage());
            warnings.add("Indicator confirmation unavailable - proceeding without confirmation");
        }

        // Step 6: Position sizing with full risk metrics
        PositionSizeResult sizing = positionSizeService.calculatePositionSize(signal, sizeAdjustment);
        if (!sizing.valid()) {
            return reject(signal, "Position size invalid after adjustments");
        }
        warnings.addAll(sizing.adjustments());

        // Step 7: Build risk/reward metrics
        double riskRewardRatio = 0.0;
        BigDecimal expectedReward = BigDecimal.ZERO;
        if (signal.getStopLoss() != null && signal.getTakeProfit() != null) {
            BigDecimal entry = signal.getPrice();
            BigDecimal risk = entry.subtract(signal.getStopLoss()).abs();
            BigDecimal reward = signal.getTakeProfit().subtract(entry).abs();
            if (risk.compareTo(BigDecimal.ZERO) > 0) {
                riskRewardRatio = reward.divide(risk, 4, RoundingMode.HALF_UP).doubleValue();
                expectedReward = sizing.riskAmount()
                        .multiply(BigDecimal.valueOf(riskRewardRatio));
            }
        }

        // Step 8: Build order with risk metrics
        Order.OrderSide side = signal.getAction() == TradeSignal.SignalAction.BUY
                ? Order.OrderSide.BUY : Order.OrderSide.SELL;
        Instant validUntil = signal.getCreatedAt() != null
                ? signal.getCreatedAt().plusSeconds(signalExpirySeconds)
                : Instant.now().plusSeconds(signalExpirySeconds);

        OrderDTO order = new OrderDTO(
                signal.getId(),
                signal.getSymbol(),
                signal.getExchange(),
                sizing.assetClass(),
                side,
                Order.OrderType.LIMIT,
                sizing.quantity(),
                signal.getPrice(),
                signal.getStopLoss(),
                signal.getTakeProfit(),
                signal.getStrategy(),
                validUntil,
                sizing.riskAmount(),
                sizing.riskPercentage(),
                sizing.portfolioHeatBefore(),
                sizing.portfolioHeatAfter(),
                expectedReward,
                riskRewardRatio,
                sizing.sizingMethod()
        );

        validatedCounter.increment();
        log.info("Signal {} VALIDATED: {} {} qty={} @ {} riskAmt={} rr={}",
                signal.getId(), signal.getAction(), signal.getSymbol(),
                sizing.quantity(), signal.getPrice(), sizing.riskAmount(), riskRewardRatio);
        return new ValidationResult(true, null, order, warnings);
    }

    private ValidationResult reject(TradeSignal signal, String reason) {
        meterRegistry.counter("tradie.signals.rejected", "reason", sanitizeTag(reason)).increment();
        log.warn("Signal {} REJECTED: {}", signal.getId(), reason);
        return new ValidationResult(false, reason, null, List.of());
    }

    private boolean isExpired(TradeSignal signal) {
        return signal.getCreatedAt() != null
                && Instant.now().isAfter(signal.getCreatedAt().plusSeconds(signalExpirySeconds));
    }

    private String sanitizeTag(String reason) {
        return reason != null && reason.length() > 50 ? reason.substring(0, 50) : reason;
    }
}
