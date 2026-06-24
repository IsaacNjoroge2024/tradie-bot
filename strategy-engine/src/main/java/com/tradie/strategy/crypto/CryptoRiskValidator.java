package com.tradie.strategy.crypto;

import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.config.CryptoProperties;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import com.tradie.strategy.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validates crypto-specific risk rules before a signal is approved.
 *
 * <p>Checks:
 * <ol>
 *   <li>Crypto trading is enabled via config</li>
 *   <li>Signal carries a stop loss — mandatory for correct exposure accounting</li>
 *   <li>Stop loss is at least {@code minStopLossPct}% away from entry (high-vol assets need wider stops)</li>
 *   <li>Current crypto exposure plus the estimated pending trade risk does not exceed
 *       {@code maxTotalExposurePct} — uses the volatility-adjusted risk percentage so the
 *       estimate matches what {@link com.tradie.strategy.crypto.CryptoPositionSizer} will compute</li>
 * </ol>
 */
@Component
public class CryptoRiskValidator {

    private static final Logger log = LoggerFactory.getLogger(CryptoRiskValidator.class);

    private final CryptoProperties cryptoProperties;
    private final PositionRepository positionRepository;
    private final AccountService accountService;
    private final CryptoAssetService cryptoAssetService;

    @Value("${tradie.position-sizing.risk-per-trade-pct:2.0}")
    private double riskPerTradePct;

    public CryptoRiskValidator(CryptoProperties cryptoProperties,
                                PositionRepository positionRepository,
                                AccountService accountService,
                                CryptoAssetService cryptoAssetService) {
        this.cryptoProperties = cryptoProperties;
        this.positionRepository = positionRepository;
        this.accountService = accountService;
        this.cryptoAssetService = cryptoAssetService;
    }

    public record CryptoValidationResult(boolean allowed, String reason) {}

    /**
     * Validates crypto-specific risk rules for the given signal.
     *
     * @param signal the trade signal to validate (must be a crypto signal)
     * @return result indicating whether the signal passes crypto risk rules
     */
    public CryptoValidationResult validate(TradeSignal signal) {
        if (!cryptoProperties.isEnabled()) {
            return new CryptoValidationResult(false, "Crypto trading is disabled");
        }

        // Stop loss is mandatory — without it the exposure calculation has no risk basis
        if (signal.getStopLoss() == null) {
            return new CryptoValidationResult(false, "Crypto signals must include a stop loss");
        }

        if (signal.getPrice() != null) {
            double stopPct = calculateStopPct(signal);
            double minStopPct = cryptoProperties.getRisk().getMinStopLossPct();
            if (stopPct < minStopPct) {
                String reason = String.format(
                        "Crypto stop loss too tight: %.2f%% (min %.1f%%)", stopPct, minStopPct);
                log.warn("Crypto risk validation failed for {}: {}", signal.getSymbol(), reason);
                return new CryptoValidationResult(false, reason);
            }
        }

        double currentExposurePct = getCryptoExposurePct();
        double pendingRiskPct = estimatePendingRiskPct(signal);
        double maxExposure = cryptoProperties.getRisk().getMaxTotalExposurePct();

        if (currentExposurePct + pendingRiskPct >= maxExposure) {
            String reason = String.format(
                    "Max crypto exposure reached: %.2f%% current + %.2f%% pending = %.2f%% (max %.1f%%)",
                    currentExposurePct, pendingRiskPct,
                    currentExposurePct + pendingRiskPct, maxExposure);
            log.warn("Crypto risk validation failed: {}", reason);
            return new CryptoValidationResult(false, reason);
        }

        return new CryptoValidationResult(true, null);
    }

    private double calculateStopPct(TradeSignal signal) {
        BigDecimal entry = signal.getPrice();
        BigDecimal sl = signal.getStopLoss();
        if (entry.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return entry.subtract(sl).abs()
                .divide(entry, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * Estimates the pending trade's risk as a percentage of the live account value.
     * Uses the volatility-adjusted risk to mirror what CryptoPositionSizer will compute.
     * Falls back to the full riskPerTradePct if the symbol spec cannot be resolved.
     */
    private double estimatePendingRiskPct(TradeSignal signal) {
        try {
            CryptoAssetSpec spec = cryptoAssetService.getAssetSpecOrDefault(signal.getSymbol());
            return riskPerTradePct / spec.volatilityMultiplier();
        } catch (Exception e) {
            log.warn("Could not resolve spec for {} to estimate pending risk, using full pct: {}",
                    signal.getSymbol(), e.getMessage());
            return riskPerTradePct;
        }
    }

    /**
     * Returns the current crypto portfolio exposure as a percentage of the live account value.
     * Uses risk-based exposure (stop distance × quantity) consistent with portfolio heat.
     * Positions without a recorded stop fall back to {@code minStopLossPct}% of notional as
     * a conservative estimate so they are never invisible to the exposure cap.
     */
    double getCryptoExposurePct() {
        double minStopPct = cryptoProperties.getRisk().getMinStopLossPct();

        BigDecimal totalRisk = positionRepository.findByStatus(Position.PositionStatus.OPEN)
                .stream()
                .filter(p -> "CRYPTO".equals(p.getAssetClass()) && p.getEntryPrice() != null)
                .map(p -> {
                    BigDecimal riskPerUnit = p.getStopLoss() != null
                            ? p.getEntryPrice().subtract(p.getStopLoss()).abs()
                            : p.getEntryPrice().multiply(BigDecimal.valueOf(minStopPct / 100.0));
                    return riskPerUnit.multiply(p.getQuantity());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double accountBalance = accountService.getAccountInfo().accountValue().doubleValue();
        if (accountBalance <= 0) return 0.0;

        return totalRisk.divide(BigDecimal.valueOf(accountBalance), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
