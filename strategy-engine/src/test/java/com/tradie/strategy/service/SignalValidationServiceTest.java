package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.NewsShieldClient;
import com.tradie.strategy.dto.MarketStatusResponse;
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

@ExtendWith(MockitoExtension.class)
class SignalValidationServiceTest {

    @Mock
    private NewsShieldClient newsShieldClient;

    @Mock
    private KillZoneService killZoneService;

    @Mock
    private RiskRuleService riskRuleService;

    @Mock
    private PositionSizer positionSizer;

    private SignalValidationService service;

    @BeforeEach
    void setUp() {
        service = new SignalValidationService(
                newsShieldClient, killZoneService, riskRuleService,
                positionSizer, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "signalExpirySeconds", 300);
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

    private void stubAllPass() {
        when(newsShieldClient.getMarketStatus(anyString()))
                .thenReturn(new MarketStatusResponse(true, "LOW", List.of()));
        when(killZoneService.validate(any()))
                .thenReturn(new KillZoneService.KillZoneResult(true, null, null));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizer.calculateQuantity(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(10));
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
        signal.setCreatedAt(Instant.now().minusSeconds(400)); // older than 300s

        ValidationResult result = service.validate(signal);

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("expired"));
        verify(newsShieldClient, never()).getMarketStatus(anyString());
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
        when(positionSizer.calculateQuantity(any(), any(), eq(BigDecimal.valueOf(0.5))))
                .thenReturn(BigDecimal.valueOf(5));

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
                .thenReturn(new KillZoneService.KillZoneResult(true, null, "Outside Kill Zone - allowed for high-confidence"));
        when(riskRuleService.validateAll(any()))
                .thenReturn(List.of(RuleResult.pass()));
        when(positionSizer.calculateQuantity(any(), any(), any()))
                .thenReturn(BigDecimal.TEN);

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
}
