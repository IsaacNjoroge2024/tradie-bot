package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.NewsShieldClient;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.MarketStatusResponse;
import com.tradie.strategy.dto.PositionSizeResult;
import com.tradie.strategy.dto.RuleResult;
import com.tradie.strategy.dto.ValidationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    private SignalValidationService service;

    private static final ConfirmationResult NO_OP_CONFIRMATION =
            new ConfirmationResult(true, 0, 0, 50.0, List.of(), List.of());

    @BeforeEach
    void setUp() {
        service = new SignalValidationService(
                newsShieldClient, killZoneService, riskRuleService,
                positionSizeService, confirmationService, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "signalExpirySeconds", 300);
        lenient().when(confirmationService.confirm(any())).thenReturn(NO_OP_CONFIRMATION);
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
}
