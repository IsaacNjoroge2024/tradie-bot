package com.tradie.alert.service;

import com.tradie.alert.client.TelegramClient;
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

import java.math.BigDecimal;
import java.util.List;

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

    private TelegramCommandHandler handler;
    private static final String CHAT_ID = "123456";

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(
                telegramClient, tradingControlService,
                positionRepository, orderRepository,
                dailySummaryService, messageFormatter);
    }

    @Test
    void handle_status_activeTrading_sendStatusMessage() {
        when(tradingControlService.isPaused()).thenReturn(false);
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(2L);

        handler.handle("/status", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
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
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().contains("PAUSED"));
    }

    @Test
    void handle_positions_noOpenPositions_sendsEmptyMessage() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        handler.handle("/positions", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString(), eq("MarkdownV2"));
    }

    @Test
    void handle_positions_withOpenPositions_listsAll() {
        Position pos = buildPosition("AAPL", "BUY", 150.00, 100, null);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(pos));

        handler.handle("/positions", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().contains("AAPL"));
    }

    @Test
    void handle_positions_withUnrealizedPnl_showsPnl() {
        Position pos = buildPosition("TSLA", "BUY", 200.00, 50, 500.00);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(pos));

        handler.handle("/positions", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().contains("P&L"));
        assertTrue(msg.getValue().contains("500"));
    }

    @Test
    void handle_pause_pausesTrading() {
        handler.handle("/pause news event", CHAT_ID);

        verify(tradingControlService).pause("news event");
        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString(), eq("MarkdownV2"));
    }

    @Test
    void handle_resume_resumesTrading() {
        handler.handle("/resume", CHAT_ID);

        verify(tradingControlService).resume();
        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString(), eq("MarkdownV2"));
    }

    @Test
    void handle_help_sendsHelpText() {
        handler.handle("/help", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().contains("/status"));
        assertTrue(msg.getValue().contains("/pause"));
    }

    @Test
    void handle_unknownCommand_sendsUnknownMessage() {
        handler.handle("/foobar", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString(), eq("MarkdownV2"));
    }

    @Test
    void handle_nonCommand_doesNothing() {
        handler.handle("hello world", CHAT_ID);

        verifyNoInteractions(telegramClient);
    }

    @Test
    void handle_cancel_noSymbol_sendsUsageHint() {
        handler.handle("/cancel", CHAT_ID);

        verify(telegramClient).sendMessage(eq(CHAT_ID), anyString(), eq("MarkdownV2"));
    }

    @Test
    void handle_cancel_withSymbol_noPendingOrders_sendsNotFound() {
        when(orderRepository.findBySymbolAndStatus("AAPL", Order.OrderStatus.PENDING))
                .thenReturn(List.of());

        handler.handle("/cancel AAPL", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().toLowerCase().contains("no pending"));
    }

    @Test
    void handle_cancel_withSymbol_hasPendingOrders_showsCount() {
        Order order = new Order();
        order.setSymbol("AAPL");
        when(orderRepository.findBySymbolAndStatus("AAPL", Order.OrderStatus.PENDING))
                .thenReturn(List.of(order));

        handler.handle("/cancel AAPL", CHAT_ID);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq(CHAT_ID), msg.capture(), eq("MarkdownV2"));
        assertTrue(msg.getValue().contains("1"));
        assertTrue(msg.getValue().toLowerCase().contains("pending"));
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
