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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SignalValidationService {

    private static final Logger log = LoggerFactory.getLogger(SignalValidationService.class);

    private final NewsShieldClient newsShieldClient;
    private final KillZoneService killZoneService;
    private final RiskRuleService riskRuleService;
    private final PositionSizer positionSizer;

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
            PositionSizer positionSizer,
            MeterRegistry meterRegistry) {
        this.newsShieldClient = newsShieldClient;
        this.killZoneService = killZoneService;
        this.riskRuleService = riskRuleService;
        this.positionSizer = positionSizer;
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

        // Step 5: Position size
        BigDecimal quantity = positionSizer.calculateQuantity(
                signal.getPrice(), signal.getStopLoss(), sizeAdjustment);

        // Step 6: Build order
        OrderDTO order = new OrderDTO(
                signal.getId(),
                signal.getSymbol(),
                signal.getExchange(),
                "STK",
                signal.getAction() == TradeSignal.SignalAction.BUY
                        ? Order.OrderSide.BUY : Order.OrderSide.SELL,
                Order.OrderType.LIMIT,
                quantity,
                signal.getPrice(),
                signal.getStopLoss(),
                signal.getTakeProfit(),
                signal.getStrategy(),
                signal.getCreatedAt() != null
                        ? signal.getCreatedAt().plusSeconds(signalExpirySeconds)
                        : Instant.now().plusSeconds(signalExpirySeconds)
        );

        validatedCounter.increment();
        log.info("Signal {} VALIDATED: {} {} qty={} @ {}",
                signal.getId(), signal.getAction(), signal.getSymbol(), quantity, signal.getPrice());
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
        // Truncate long reasons for Micrometer tag cardinality safety
        return reason != null && reason.length() > 50 ? reason.substring(0, 50) : reason;
    }
}
