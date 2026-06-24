package com.tradie.strategy.crypto;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.config.CryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoRiskValidatorTest {

    @Mock
    private PositionRepository positionRepository;

    private CryptoProperties cryptoProperties;
    private CryptoRiskValidator validator;

    @BeforeEach
    void setUp() {
        cryptoProperties = new CryptoProperties();
        cryptoProperties.setEnabled(true);
        CryptoProperties.Risk risk = new CryptoProperties.Risk();
        risk.setMinStopLossPct(3.0);
        risk.setMaxTotalExposurePct(15.0);
        cryptoProperties.setRisk(risk);

        validator = new CryptoRiskValidator(cryptoProperties, positionRepository);
        ReflectionTestUtils.setField(validator, "defaultAccountBalance", 10000.0);
    }

    // ─── enabled check ────────────────────────────────────────────────────────

    @Test
    void validate_cryptoDisabled_rejects() {
        cryptoProperties.setEnabled(false);

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(buildSignal("BTC", 42500, 40000, 47000));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("disabled");
    }

    // ─── stop loss tightness ──────────────────────────────────────────────────

    @Test
    void validate_stopLossTooTight_rejects() {
        // entry=42500, stop=42000 → stopPct = 500/42500 = 1.18% < 3%
        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("BTC", 42500, 42000, 47000));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("too tight");
    }

    @Test
    void validate_stopLossWideEnough_passes() {
        // entry=42500, stop=41225 → stopPct = 1275/42500 = 3.0% (exactly at limit)
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("BTC", 42500, 41225, 47000));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_stopLossWell_passes() {
        // entry=42500, stop=40375 → stopPct = 2125/42500 = 5.0% > 3%
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("BTC", 42500, 40375, 47000));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_noStopLoss_skipsTightnessCheck() {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("BTC");
        signal.setPrice(BigDecimal.valueOf(42500));
        signal.setAction(TradeSignal.SignalAction.BUY);
        // No stop loss → tightness check is skipped; exposure check still runs
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(signal);

        assertThat(result.allowed()).isTrue();
    }

    // ─── exposure limit ───────────────────────────────────────────────────────

    @Test
    void validate_maxExposureReached_rejects() {
        // Create two open crypto positions that together exceed 15% exposure
        // pos1: BTC, entry=42500, stop=40375, qty=0.1 → risk = 0.1 × 2125 = $212.50 = 2.125%
        // pos2: ETH, entry=2000, stop=1700, qty=0.5 → risk = 0.5 × 300 = $150 = 1.5%
        // Need total ≥ 15% ($1500 on $10k account)
        // So add enough: pos3 with huge risk
        Position p1 = buildCryptoPosition("BTC", 42500, 40375, 0.1);
        Position p2 = buildCryptoPosition("ETH", 2000, 1700, 5.0); // risk = 5.0 × 300 = $1500 = 15%

        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(p1, p2));

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("LTC", 100, 95, 115));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Max crypto exposure");
    }

    @Test
    void validate_underExposureLimit_passes() {
        // risk = 0.1 × 2125 = $212.50 = 2.125% (well below 15%)
        Position p1 = buildCryptoPosition("BTC", 42500, 40375, 0.1);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(p1));

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("ETH", 2000, 1900, 2300));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validate_nonCryptoPositionsIgnoredInExposure() {
        // STK position with huge risk — must NOT count toward crypto exposure
        Position stock = new Position();
        stock.setAssetClass("STK");
        stock.setEntryPrice(BigDecimal.valueOf(100));
        stock.setStopLoss(BigDecimal.valueOf(1));     // extreme risk
        stock.setQuantity(BigDecimal.valueOf(1000));
        stock.setStatus(Position.PositionStatus.OPEN);

        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(stock));

        CryptoRiskValidator.CryptoValidationResult result = validator.validate(
                buildSignal("BTC", 42500, 40375, 47000));

        assertThat(result.allowed()).isTrue();
    }

    // ─── getCryptoExposurePct ─────────────────────────────────────────────────

    @Test
    void getCryptoExposurePct_noOpenPositions_returnsZero() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        assertThat(validator.getCryptoExposurePct()).isEqualTo(0.0);
    }

    @Test
    void getCryptoExposurePct_positionsWithoutStop_excludedFromCalculation() {
        Position p = new Position();
        p.setAssetClass("CRYPTO");
        p.setEntryPrice(BigDecimal.valueOf(42500));
        p.setStopLoss(null);
        p.setQuantity(BigDecimal.valueOf(1.0));
        p.setStatus(Position.PositionStatus.OPEN);

        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(p));

        assertThat(validator.getCryptoExposurePct()).isEqualTo(0.0);
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
