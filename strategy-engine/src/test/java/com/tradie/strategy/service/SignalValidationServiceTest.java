package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.NewsShieldClient;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.MarketStatusResponse;
import com.tradie.strategy.dto.PositionSizeResult;
import com.tradie.strategy.dto.RuleResult;
import com.tradie.strategy.dto.ValidationResult;
import com.tradie.strategy.crypto.CryptoAssetService;
import com.tradie.strategy.crypto.CryptoMarketHours;
import com.tradie.strategy.crypto.CryptoRiskValidator;
import com.tradie.strategy.crypto.dto.CryptoAssetSpec;
import com.tradie.strategy.futures.FuturesContractService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.tradie.strategy.futures.dto.FuturesSpec;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SignalValidationServiceTest {

    @Mock
    private NewsShieldClient newsShieldClient;

    @Mock
    private KillZoneService killZoneService;

    @Mock
    private RiskRuleService riskRuleService;

    @Mock
    private PositionSizeService positionSizeService;

    @Mock
    private SignalConfirmationService confirmationService;

    @Mock
    private FuturesContractService futuresContractService;

    @Mock
    private CryptoRiskValidator cryptoRiskValidator;

    @Mock
    private CryptoMarketHours cryptoMarketHours;

    @Mock
    private CryptoAssetService cryptoAssetService;

    @Mock
    private RegimeAwareValidator regimeAwareValidator;

    private SignalValidationService service;

    private static final ConfirmationResult NO_OP_CONFIRMATION =
            new ConfirmationResult(true, 0, 0, 50.0, List.of(), List.of());

    @BeforeEach
    void setUp() {
        service = new SignalValidationService(
                newsShieldClient, killZoneService, riskRuleService,
                positionSizeService, confirmationService,
                futuresContractService, cryptoRiskValidator, cryptoMarketHours,
                cryptoAssetService, regimeAwareValidator, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "signalExpirySeconds", 300);
        lenient().when(confirmationService.confirm(any())).thenReturn(NO_OP_CONFIRMATION);
        lenient().when(cryptoMarketHours.shouldTrade(any())).thenReturn(true);
        lenient().when(regimeAwareValidator.validate(any()))
                .thenReturn(RegimeAwareValidator.RegimeValidationResult.approved("ranging", BigDecimal.ONE));
    }

    private TradeSignal freshSignal() {
        TradeSignal s = new TradeSignal();
        s.setSymbol("AAPL");
        s.setAction(TradeSignal.SignalAction.BUY);
        s.setPrice(BigDecimal.valueOf(150));
        s.setStopLoss(BigDecimal.valueOf(145));
        s.setTakeProfit(BigDecimal.valueOf(165));
        s.setStrategy("FVG");
        s.setExchange("NASDAQ");
        s.setCreatedAt(Instant.now());
        return s;
    }

    private PositionSizeResult validSizing(BigDecimal quantity) {
        return new PositionSizeResult(
                quantity, BigDecimal.valueOf(200), 2.0,
                "FIXED_FRACTIONAL", "STK", List.of(), true, 0.0, 2.0);
    }

    private void stubAllPass() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(validSizing(BigDecimal.valueOf(10)));
    }

    @Test
    void validate_futures_resolvesAndSetsContractMonth() {
        TradeSignal signal = freshSignal();
        signal.setSymbol("ES");
        FuturesSpec spec = new FuturesSpec("ES", "ESM5", "202506", "CME", 50.0, 0.25, 12.50, null, 12000.0, 10800.0);
        when(newsShieldClient.getMarketStatus("ES"))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(new PositionSizeResult(
                        BigDecimal.TEN, BigDecimal.valueOf(200), 2.0,
                        "FIXED_FRACTIONAL", "FUTURES", List.of(), true, 0.0, 2.0));
        when(futuresContractService.getFrontMonthContract("ES")).thenReturn(Optional.of(spec));

        ValidationResult result = service.validate(signal);

        assertTrue(result.approved());
        assertEquals("202506", result.order().contractMonth());
        verify(futuresContractService).getFrontMonthContract("ES");
    }

    @Test
    void validate_allChecksPass_returnsApproved() {
        stubAllPass();
        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertNull(result.rejectionReason());
        assertNotNull(result.order());
    }

    @Test
    void validate_expiredSignal_rejected() {
        TradeSignal signal = freshSignal();
        signal.setCreatedAt(Instant.now().minusSeconds(400));

        ValidationResult result = service.validate(signal);

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("expired"));
        verify(newsShieldClient, never()).getMarketStatus(anyString());
    }

    @Test
    void validate_newsShieldException_failOpen_continuesValidation() {
        when(newsShieldClient.getMarketStatus(anyString())).thenThrow(new RuntimeException("Connection refused"));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any())).thenReturn(List.of(RuleResult.pass()));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(validSizing(BigDecimal.valueOf(10)));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("News Shield unavailable")));
    }

    @Test
    void validate_newsShieldUnsafe_rejected() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(false, "HIGH",
                        List.of("FOMC meeting in 30 minutes")));

        ValidationResult result = service.validate(freshSignal());

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("News Shield"));
        verify(killZoneService, never()).validate(any());
    }

    @Test
    void validate_outsideKillZone_rejected() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(false, "Outside Kill Zone", null));

        ValidationResult result = service.validate(freshSignal());

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("Outside Kill Zone"));
        verify(riskRuleService, never()).validateAll(any());
    }

    @Test
    void validate_riskRuleFails_rejected() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.fail("Daily loss limit reached")));

        ValidationResult result = service.validate(freshSignal());

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("Daily loss limit"));
    }

    @Test
    void validate_losingStreakAdjustment_warningAddedAndOrderHasReducedQty() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.passWithAdjustment(BigDecimal.valueOf(0.5))));
        when(positionSizeService.calculatePositionSize(any(), eq(BigDecimal.valueOf(0.5))))
                .thenReturn(validSizing(BigDecimal.valueOf(5)));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertFalse(result.warnings().isEmpty());
        assertEquals(BigDecimal.valueOf(5), result.order().quantity());
    }

    @Test
    void validate_killZoneWarning_includedInResult() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null,
                        "Outside Kill Zone - allowed for high-confidence"));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(validSizing(BigDecimal.TEN));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("high-confidence"));
    }

    @Test
    void validate_orderDTO_hasCorrectFields() {
        stubAllPass();
        TradeSignal signal = freshSignal();
        ValidationResult result = service.validate(signal);

        assertTrue(result.approved());
        assertEquals("AAPL", result.order().symbol());
        assertEquals("NASDAQ", result.order().exchange());
        assertEquals(BigDecimal.valueOf(10), result.order().quantity());
        assertEquals(BigDecimal.valueOf(150), result.order().limitPrice());
    }

    @Test
    void validate_orderDTO_hasRiskMetrics() {
        stubAllPass();
        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertNotNull(result.order().riskAmount());
        assertEquals(0.0, result.order().portfolioHeatBefore(), 0.001);
        assertEquals(2.0, result.order().portfolioHeatAfter(), 0.01);
        assertEquals("FIXED_FRACTIONAL", result.order().sizingMethod());
        assertEquals(3.0, result.order().riskRewardRatio(), 0.001);
    }

    @Test
    void validate_confirmationAdjustsConfidence_andAddsConflictWarning() {
        stubAllPass();
        TradeSignal signal = freshSignal();
        signal.setConfidenceScore(60.0);
        when(confirmationService.confirm(any())).thenReturn(
                new ConfirmationResult(true, 3, 4, 75.0,
                        List.of("RSI", "EMA50", "MACD"), List.of("ADX")));

        ValidationResult result = service.validate(signal);

        assertTrue(result.approved());
        assertEquals(75.0, signal.getConfidenceScore(), 0.001);
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Conflicting indicators")));
    }

    @Test
    void validate_confirmationException_failOpen_stillApproves() {
        stubAllPass();
        when(confirmationService.confirm(any())).thenThrow(new RuntimeException("indicator unavailable"));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("confirmation unavailable")));
        verify(positionSizeService).calculatePositionSize(any(), any());
    }

    @Test
    void validate_positionSizeInvalid_rejected() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(new PositionSizeResult(
                        BigDecimal.ZERO, BigDecimal.ZERO, 0.0,
                        "FIXED_FRACTIONAL", "STK", List.of(), false, 0.0, 0.0));

        ValidationResult result = service.validate(freshSignal());

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("Position size invalid"));
    }

    // ─── Crypto-specific tests ────────────────────────────────────────────────

    @Test
    void validate_cryptoSignal_skipsKillZoneAndUsesCryptoMarketHours() {
        TradeSignal signal = freshSignal();
        signal.setSymbol("BTC");
        signal.setExchange("PAXOS");
        when(newsShieldClient.getMarketStatus("BTC"))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(cryptoMarketHours.shouldTrade(any())).thenReturn(true);
        when(riskRuleService.validateAll(any())).thenReturn(List.of(RuleResult.pass()));
        when(cryptoRiskValidator.validate(any()))
                .thenReturn(new CryptoRiskValidator.CryptoValidationResult(true, null));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(validSizing(BigDecimal.valueOf(0.0023)));

        ValidationResult result = service.validate(signal);

        assertTrue(result.approved());
        verify(killZoneService, never()).validate(any());
        verify(cryptoMarketHours).shouldTrade(any());
        verify(cryptoRiskValidator).validate(signal);
    }

    @Test
    void validate_cryptoSignalLowVolumeHours_rejected() {
        TradeSignal signal = freshSignal();
        signal.setSymbol("BTC");
        signal.setExchange("PAXOS");
        when(newsShieldClient.getMarketStatus("BTC"))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(cryptoMarketHours.shouldTrade(any())).thenReturn(false);

        ValidationResult result = service.validate(signal);

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("low-volume hours"));
        verify(cryptoRiskValidator, never()).validate(any());
    }

    @Test
    void validate_cryptoRiskValidationFails_rejected() {
        TradeSignal signal = freshSignal();
        signal.setSymbol("ETH");
        signal.setExchange("PAXOS");
        when(newsShieldClient.getMarketStatus("ETH"))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(riskRuleService.validateAll(any())).thenReturn(List.of(RuleResult.pass()));
        when(cryptoRiskValidator.validate(any()))
                .thenReturn(new CryptoRiskValidator.CryptoValidationResult(false,
                        "Crypto stop loss too tight: 1.50% (min 3.0%)"));

        ValidationResult result = service.validate(signal);

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("too tight"));
        verify(positionSizeService, never()).calculatePositionSize(any(), any());
    }

    @Test
    void validate_cryptoSignalBySymbol_noExchangeSet_detectedAsCrypto() {
        TradeSignal signal = freshSignal();
        signal.setSymbol("BTC");
        signal.setExchange(null);
        when(cryptoAssetService.getAssetSpec("BTC")).thenReturn(Optional.of(
                new CryptoAssetSpec("BTC", "Bitcoin",
                        BigDecimal.valueOf(0.0001), BigDecimal.valueOf(0.0001), 2, 3.0, true)));
        when(newsShieldClient.getMarketStatus("BTC"))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(cryptoMarketHours.shouldTrade(any())).thenReturn(true);
        when(riskRuleService.validateAll(any())).thenReturn(List.of(RuleResult.pass()));
        when(cryptoRiskValidator.validate(any()))
                .thenReturn(new CryptoRiskValidator.CryptoValidationResult(true, null));
        when(positionSizeService.calculatePositionSize(any(), any()))
                .thenReturn(validSizing(BigDecimal.valueOf(0.001)));

        ValidationResult result = service.validate(signal);

        assertTrue(result.approved());
        verify(killZoneService, never()).validate(any());
    }

    @Test
    void validate_nonCryptoSignal_usesKillZoneNotCryptoMarketHours() {
        stubAllPass();
        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        verify(killZoneService).validate(any());
        verify(cryptoMarketHours, never()).shouldTrade(any());
        verify(cryptoRiskValidator, never()).validate(any());
    }

    // ─── Regime-aware validation tests (Ticket 24) ─────────────────────────────

    @Test
    void validate_regimeAdvisesAgainstStrategy_rejected() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(regimeAwareValidator.validate(any())).thenReturn(
                RegimeAwareValidator.RegimeValidationResult.rejected(
                        "ranging", "Strategy FVG not recommended in ranging regime"));

        ValidationResult result = service.validate(freshSignal());

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("not recommended"));
        verify(positionSizeService, never()).calculatePositionSize(any(), any());
    }

    @Test
    void validate_regimeReducesPositionSize_adjustmentAppliedAndWarned() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(regimeAwareValidator.validate(any())).thenReturn(
                RegimeAwareValidator.RegimeValidationResult.approved("volatile", BigDecimal.valueOf(0.5)));
        when(positionSizeService.calculatePositionSize(any(), eq(BigDecimal.valueOf(0.5))))
                .thenReturn(validSizing(BigDecimal.valueOf(5)));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertEquals(BigDecimal.valueOf(5), result.order().quantity());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Regime-adjusted")));
    }

    @Test
    void validate_regimeDetectionException_failOpen_stillApproves() {
        stubAllPass();
        when(regimeAwareValidator.validate(any())).thenThrow(new RuntimeException("ML Service unavailable"));

        ValidationResult result = service.validate(freshSignal());

        assertTrue(result.approved());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Regime detection unavailable")));
    }
}
