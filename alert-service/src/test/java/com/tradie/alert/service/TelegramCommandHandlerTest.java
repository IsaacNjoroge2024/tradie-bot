package com.tradie.alert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerTest {

    @Mock private TelegramClient telegramClient;
    @Mock private TradingControlService tradingControlService;
    @Mock private PositionRepository positionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DailySummaryService dailySummaryService;
    @Mock private MessageFormatter messageFormatter;
    @Mock private RestTemplate restTemplate;

    private TelegramCommandHandler handler;
    private static final String CHAT_ID = "123456";

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(
                telegramClient, tradingControlService,
                positionRepository, orderRepository,
                dailySummaryService, messageFormatter,
                restTemplate, "http://localhost:8082",
                new TelegramProperties());
    }

    @Test
    void handle_status_activeTrading_sendStatusMessage() {
        when(tradingControlService.isPaused()).thenReturn(false);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(2L);

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("ACTIVE"));
        assertTrue(msg.getValue().contains("2"));
    }

    @Test
    void handle_status_tradingPaused_showsPausedState() {
        when(tradingControlService.isPaused()).thenReturn(true);
        when(tradingControlService.getPauseReason()).thenReturn("manual");
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("PAUSED"));
    }

    @Test
    void handle_status_ibkrConnected_showsConnectedStatus() {
        when(tradingControlService.isPaused()).thenReturn(false);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);
        ObjectNode statusNode = new ObjectMapper().createObjectNode();
        statusNode.put("connected", true);
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(statusNode);

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("CONNECTED"));
    }

    @Test
    void handle_status_ibkrDisconnected_showsDisconnectedStatus() {
        when(tradingControlService.isPaused()).thenReturn(false);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);
        ObjectNode statusNode = new ObjectMapper().createObjectNode();
        statusNode.put("connected", false);
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class))).thenReturn(statusNode);

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("DISCONNECTED"));
    }

    @Test
    void handle_status_ibkrUnreachable_showsUnknownStatus() {
        when(tradingControlService.isPaused()).thenReturn(false);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);
        when(restTemplate.getForObject(anyString(), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("UNKNOWN"));
    }

    @Test
    void handle_positions_noOpenPositions_sendsEmptyMessage() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        handler.handle("/positions", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString());
    }

    @Test
    void handle_positions_withOpenPositions_listsAll() {
        Position pos = buildPosition("AAPL", "BUY", 150.00, 100, null);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(pos));

        handler.handle("/positions", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("AAPL"));
    }

    @Test
    void handle_positions_withUnrealizedPnl_showsPnl() {
        Position pos = buildPosition("TSLA", "BUY", 200.00, 50, 500.00);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(pos));

        handler.handle("/positions", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("P&L"));
        assertTrue(msg.getValue().contains("500"));
    }

    @Test
    void handle_pause_pausesTrading() {
        handler.handle("/pause news event", CHAT_ID);

        verify(tradingControlService).pause("news event");
        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString());
    }

    @Test
    void handle_resume_resumesTrading() {
        handler.handle("/resume", CHAT_ID);

        verify(tradingControlService).resume();
        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString());
    }

    @Test
    void handle_help_sendsHelpText() {
        handler.handle("/help", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("/status"));
        assertTrue(msg.getValue().contains("/pause"));
    }

    @Test
    void handle_unknownCommand_sendsUnknownMessage() {
        handler.handle("/foobar", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString());
    }

    @Test
    void handle_nonCommand_doesNothing() {
        handler.handle("hello world", CHAT_ID);

        verifyNoInteractions(telegramClient);
    }

    @Test
    void handle_cancel_noSymbol_sendsUsageHint() {
        handler.handle("/cancel", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString());
    }

    @Test
    void handle_cancel_withSymbol_noPendingOrders_sendsNotFound() {
        when(orderRepository.findBySymbolAndStatusIn(eq("AAPL"), any()))
                .thenReturn(List.of());

        handler.handle("/cancel AAPL", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().toLowerCase().contains("no active"));
    }

    @Test
    void handle_cancel_withSymbol_hasPendingOrders_showsCount() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(UUID.randomUUID());
        when(orderRepository.findBySymbolAndStatusIn(eq("AAPL"), any()))
                .thenReturn(List.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        handler.handle("/cancel AAPL", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture());
        assertTrue(msg.getValue().contains("1"));
        assertTrue(msg.getValue().toLowerCase().contains("cancelled"));
    }

    @Test
    void handle_unauthorizedChatId_isRejected() {
        TelegramProperties restrictedProps = new TelegramProperties();
        restrictedProps.setChatId("111111");

        TelegramCommandHandler restricted = new TelegramCommandHandler(
                telegramClient, tradingControlService, positionRepository,
                orderRepository, dailySummaryService, messageFormatter,
                restTemplate, "http://localhost:8082", restrictedProps);

        restricted.handle("/status", "999999");

        verifyNoInteractions(telegramClient);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildPosition(String symbol, String side, double price, int qty,
                                   Double unrealizedPnl) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setSide(Order.OrderSide.valueOf(side));
        p.setEntryPrice(BigDecimal.valueOf(price));
        p.setQuantity(BigDecimal.valueOf(qty));
        p.setStatus(Position.PositionStatus.OPEN);
        if (unrealizedPnl != null) {
            p.setUnrealizedPnl(BigDecimal.valueOf(unrealizedPnl));
        }
        return p;
    }
}
