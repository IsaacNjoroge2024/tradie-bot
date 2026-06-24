package com.tradie.strategy.crypto;

import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.config.CryptoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates crypto-specific risk rules before a signal is approved.
 *
 * <p>Checks:
 * <ol>
 *   <li>Crypto trading is enabled via config</li>
 *   <li>Stop loss is at least {@code minStopLossPct}% away from entry (high-vol assets need wider stops)</li>
 *   <li>Total open crypto exposure does not exceed {@code maxTotalExposurePct}</li>
 * </ol>
 */
@Component
public class CryptoRiskValidator {

    private static final Logger log = LoggerFactory.getLogger(CryptoRiskValidator.class);

    private final CryptoProperties cryptoProperties;
    private final PositionRepository positionRepository;

    @Value("${tradie.risk.default-account-balance:10000.0}")
    private double defaultAccountBalance;

    public CryptoRiskValidator(CryptoProperties cryptoProperties,
                                PositionRepository positionRepository) {
        this.cryptoProperties = cryptoProperties;
        this.positionRepository = positionRepository;
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

        if (signal.getStopLoss() != null && signal.getPrice() != null) {
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
        double maxExposure = cryptoProperties.getRisk().getMaxTotalExposurePct();
        if (currentExposurePct >= maxExposure) {
            String reason = String.format(
                    "Max crypto exposure reached: %.2f%% (max %.1f%%)", currentExposurePct, maxExposure);
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
     * Returns the current crypto portfolio exposure as a percentage of account balance.
     * Uses risk-based exposure (stop distance × quantity) consistent with portfolio heat.
     */
    double getCryptoExposurePct() {
        List<Position> cryptoPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN)
                .stream()
                .filter(p -> "CRYPTO".equals(p.getAssetClass()))
                .collect(Collectors.toList());

        BigDecimal totalRisk = cryptoPositions.stream()
                .filter(p -> p.getStopLoss() != null)
                .map(p -> p.getEntryPrice().subtract(p.getStopLoss()).abs().multiply(p.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (defaultAccountBalance <= 0) return 0.0;

        return totalRisk.divide(BigDecimal.valueOf(defaultAccountBalance), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
