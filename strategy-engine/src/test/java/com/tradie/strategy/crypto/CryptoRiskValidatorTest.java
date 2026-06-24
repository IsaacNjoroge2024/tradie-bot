package com.tradie.strategy.crypto;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.config.CryptoProperties;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import com.tradie.strategy.dto.AccountInfo;
import com.tradie.strategy.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoRiskValidatorTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CryptoAssetService cryptoAssetService;

    private CryptoProperties cryptoProperties;
    private CryptoRiskValidator validator;

    // BTC: volMult=3.0 → pendingRiskPct = 2.0/3.0 ≈ 0.667%
    private static final CryptoAssetSpec BTC_SPEC = new CryptoAssetSpec(
            "BTC", "Bitcoin",
            BigDecimal.valueOf(0.0001), BigDecimal.valueOf(0.0001), 2, 3.0, true);

    private static final CryptoAssetSpec ETH_SPEC = new CryptoAssetSpec(
            "ETH", "Ethereum",
            BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.001), 2, 3.5, true);

    private static final CryptoAssetSpec LTC_SPEC = new CryptoAssetSpec(
            "LTC", "Litecoin",
            BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.01), 2, 4.0, true);

    @BeforeEach
    void setUp() {
        cryptoProperties = new CryptoProperties();
        cryptoProperties.setEnabled(true);
        CryptoProperties.Risk risk = new CryptoProperties.Risk();
        risk.setMinStopLossPct(3.0);
        risk.setMaxTotalExposurePct(15.0);
        cryptoProperties.setRisk(risk);

        validator = new CryptoRiskValidator(
                cryptoProperties, positionRepository, accountService, cryptoAssetService);
        ReflectionTestUtils.setField(validator, "riskPerTradePct", 2.0);

        // Default account balance for exposure calculations
        lenient().when(accountService.getAccountInfo()).thenReturn(
                new AccountInfo(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(10000), BigDecimal.ZERO));

        // Default spec for pending-risk estimation (overridden per test where needed)
        lenient().when(cryptoAssetService.getAssetSpecOrDefault(anyString())).thenReturn(BTC_SPEC);
    }

    // ─── enabled check ────────────────────────────────────────────────────────

    @Test
    void validate_cryptoDisabled_rejects() {
        cryptoProperties.setEnabled(false);

        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 42500, 40000, 47000));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("disabled");
    }

    // ─── stop loss tightness ──────────────────────────────────────────────────

    @Test
    void validate_stopLossTooTight_rejects() {
        // entry=42500, stop=42000 → stopPct = 500/42500 = 1.18% < 3%
        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 42500, 42000, 47000));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("too tight");
    }

    @Test
    void validate_stopLossWideEnough_passes() {
        // entry=42500, stop=41225 → stopPct = 1275/42500 = 3.0% (exactly at limit)
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 42500, 41225, 47000));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_stopLossWell_passes() {
        // entry=42500, stop=40375 → stopPct = 2125/42500 = 5.0% > 3%
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 42500, 40375, 47000));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_noStopLoss_rejects() {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("BTC");
        signal.setPrice(BigDecimal.valueOf(42500));
        signal.setAction(TradeSignal.SignalAction.BUY);
        // No stop loss → rejected immediately; exposure check is never reached

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(signal);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("stop loss");
    }

    // ─── exposure limit (current + pending) ───────────────────────────────────

    @Test
    void validate_maxExposureReached_rejects() {
        // p1: BTC risk = 0.1 × 2125 = $212.50 = 2.125% of $10k
        // p2: ETH risk = 5.0 × 300  = $1500   = 15.0%  of $10k
        // currentExposurePct ≈ 17.125% — already above limit before pending
        Position p1 = buildCryptoPosition("BTC", 42500, 40375, 0.1);
        Position p2 = buildCryptoPosition("ETH", 2000, 1700, 5.0);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(p1, p2));
        when(cryptoAssetService.getAssetSpecOrDefault("LTC")).thenReturn(LTC_SPEC);

        // LTC signal: entry=100, stop=95 → stopPct=5% ≥ 3% (passes tightness)
        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("LTC", 100, 95, 115));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Max crypto exposure");
    }

    @Test
    void validate_underExposureLimit_passes() {
        // p1 risk = 0.1 × 2125 = $212.50 = 2.125%; pendingBTC ≈ 0.667% → total ≈ 2.79% < 15%
        Position p1 = buildCryptoPosition("BTC", 42500, 40375, 0.1);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(p1));
        when(cryptoAssetService.getAssetSpecOrDefault("ETH")).thenReturn(ETH_SPEC);

        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("ETH", 2000, 1900, 2300));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_atExposureThresholdIncludingPending_rejects() {
        // Set up existing exposure at 14.5%; pending BTC ≈ 0.667% → 15.167% ≥ 15%
        // Use a position that gives exactly 14.5%: $1450 risk on $10k
        // e.g. qty=1.0, entry=43950, stop=42500 → risk = 1.0 × 1450 = $1450 = 14.5%
        Position p1 = buildCryptoPosition("BTC", 43950, 42500, 1.0);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(p1));
        // BTC pending: riskPerTradePct(2.0) / volMult(3.0) ≈ 0.667% → total ≈ 15.167%

        // ETH signal: entry=2000, stop=1900 → stopPct=5% ≥ 3% (passes tightness)
        when(cryptoAssetService.getAssetSpecOrDefault("BTC")).thenReturn(BTC_SPEC);
        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 2000, 1900, 2300));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Max crypto exposure");
    }

    @Test
    void validate_nonCryptoPositionsIgnoredInExposure() {
        // Stock position with huge risk must NOT count toward crypto exposure
        Position stock = new Position();
        stock.setAssetClass("STK");
        stock.setEntryPrice(BigDecimal.valueOf(100));
        stock.setStopLoss(BigDecimal.valueOf(1));
        stock.setQuantity(BigDecimal.valueOf(1000));
        stock.setStatus(Position.PositionStatus.OPEN);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(stock));

        CryptoRiskValidator.CryptoValidationResult result =
                validator.validate(buildSignal("BTC", 42500, 40375, 47000));

        assertThat(result.allowed()).isTrue();
    }

    // ─── getCryptoExposurePct ─────────────────────────────────────────────────

    @Test
    void getCryptoExposurePct_noOpenPositions_returnsZero() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        assertThat(validator.getCryptoExposurePct()).isEqualTo(0.0);
    }

    @Test
    void getCryptoExposurePct_positionsWithoutStop_usesMinStopFallback() {
        // entry=42500, stop=null, qty=1.0, minStopLossPct=3.0%
        // Conservative risk = 42500 × 0.03 × 1.0 = $1275 → 12.75% of $10k
        Position p = new Position();
        p.setAssetClass("CRYPTO");
        p.setEntryPrice(BigDecimal.valueOf(42500));
        p.setStopLoss(null);
        p.setQuantity(BigDecimal.valueOf(1.0));
        p.setStatus(Position.PositionStatus.OPEN);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(p));

        assertThat(validator.getCryptoExposurePct()).isCloseTo(12.75, offset(0.01));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private TradeSignal buildSignal(String symbol, double entry, double stop, double takeProfit) {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol(symbol);
        signal.setPrice(BigDecimal.valueOf(entry));
        signal.setStopLoss(BigDecimal.valueOf(stop));
        signal.setTakeProfit(BigDecimal.valueOf(takeProfit));
        signal.setAction(TradeSignal.SignalAction.BUY);
        return signal;
    }

    private Position buildCryptoPosition(String symbol, double entry, double stop, double qty) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setAssetClass("CRYPTO");
        p.setSide(Order.OrderSide.BUY);
        p.setEntryPrice(BigDecimal.valueOf(entry));
        p.setStopLoss(BigDecimal.valueOf(stop));
        p.setQuantity(BigDecimal.valueOf(qty));
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }
}
