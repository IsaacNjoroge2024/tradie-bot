package com.tradie.executor.order;

import com.ib.client.Decimal;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import com.tradie.common.service.AuditLogger;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.journal.TradeJournalService;
import com.tradie.executor.metrics.TradingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusTrackerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private OrderEventPublisher orderEventPublisher;
    @Mock private IBConnectionManager connectionManager;
    @Mock private TradingMetrics tradingMetrics;
    @Mock private AuditLogger auditLogger;
    @Mock private TradeJournalService tradeJournalService;

    private OrderStatusTracker tracker;

    private static final int PARENT_ID = 100;
    private static final int TP_ID     = 101;
    private static final int SL_ID     = 102;
    private static final UUID SIGNAL_ID = UUID.randomUUID();
    private static final UUID PARENT_DB_ID = UUID.randomUUID();
    private static final UUID TP_DB_ID     = UUID.randomUUID();
    private static final UUID SL_DB_ID     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tracker = new OrderStatusTracker(orderRepository, positionRepository, orderEventPublisher,
                tradingMetrics, auditLogger, tradeJournalService);
        tracker.setConnectionManager(connectionManager);
        tracker.registerOrderGroup(buildOrderGroup());
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    @Test
    void registerOrderGroup_tracksAllThreeIds() {
        // After setUp(), all 3 IDs are registered — confirmed by group count
        assertThat(tracker.getActiveGroupCount()).isEqualTo(1);
    }

    @Test
    void handleStatusUpdate_unknownOrderId_isIgnored() {
        tracker.handleStatusUpdate(999, "Filled", Decimal.ZERO, Decimal.ZERO, 150.0);

        verifyNoInteractions(orderRepository, positionRepository, orderEventPublisher);
    }

    // ─── Submitted ────────────────────────────────────────────────────────────

    @Test
    void handleStatusUpdate_submitted_updatesOrderStatus() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleStatusUpdate(PARENT_ID, "Submitted", Decimal.ZERO, Decimal.get(10), 0.0);

        verify(orderRepository).save(dbOrder);
        assertThat(dbOrder.getStatus()).isEqualTo(Order.OrderStatus.SUBMITTED);
    }

    // ─── Parent Fill ──────────────────────────────────────────────────────────

    @Test
    void handleStatusUpdate_parentFilled_createsPosition() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleStatusUpdate(PARENT_ID, "Filled", Decimal.get(10), Decimal.ZERO, 150.50);

        ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(posCaptor.capture());
        Position saved = posCaptor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getEntryPrice()).isEqualByComparingTo(BigDecimal.valueOf(150.50));
        assertThat(saved.getStatus()).isEqualTo(Position.PositionStatus.OPEN);
    }

    @Test
    void handleStatusUpdate_parentFilled_publishesFilledEvent() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleStatusUpdate(PARENT_ID, "Filled", Decimal.get(10), Decimal.ZERO, 150.50);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("FILLED");
        assertThat(eventCaptor.getValue().symbol()).isEqualTo("AAPL");
    }

    // ─── Child Fill (Take-Profit) ─────────────────────────────────────────────

    @Test
    void handleStatusUpdate_takeProfitFilled_closesPositionAndCancelsSL() {
        Order dbOrder = buildDbOrder(TP_ID);
        when(orderRepository.findByIbOrderId(TP_ID)).thenReturn(Optional.of(dbOrder));

        Order siblingOrder = buildDbOrder(SL_ID);
        when(orderRepository.findByIbOrderId(SL_ID)).thenReturn(Optional.of(siblingOrder));

        Position openPosition = buildOpenPosition();
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(openPosition));
        when(connectionManager.isConnected()).thenReturn(true);

        tracker.handleStatusUpdate(TP_ID, "Filled", Decimal.get(10), Decimal.ZERO, 165.00);

        verify(positionRepository).save(openPosition);
        assertThat(openPosition.getStatus()).isEqualTo(Position.PositionStatus.CLOSED);
        assertThat(openPosition.getExitPrice()).isEqualByComparingTo(BigDecimal.valueOf(165.00));

        verify(connectionManager).cancelOrder(SL_ID);
        assertThat(siblingOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    @Test
    void handleStatusUpdate_takeProfitFilled_publishesTakeProfitEvent() {
        Order dbOrder = buildDbOrder(TP_ID);
        when(orderRepository.findByIbOrderId(TP_ID)).thenReturn(Optional.of(dbOrder));
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());
        when(connectionManager.isConnected()).thenReturn(true);

        tracker.handleStatusUpdate(TP_ID, "Filled", Decimal.get(10), Decimal.ZERO, 165.00);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("TAKE_PROFIT_HIT");
    }

    // ─── Child Fill (Stop-Loss) ───────────────────────────────────────────────

    @Test
    void handleStatusUpdate_stopLossFilled_closesPositionAndCancelsTP() {
        Order dbOrder = buildDbOrder(SL_ID);
        when(orderRepository.findByIbOrderId(SL_ID)).thenReturn(Optional.of(dbOrder));

        Order siblingOrder = buildDbOrder(TP_ID);
        when(orderRepository.findByIbOrderId(TP_ID)).thenReturn(Optional.of(siblingOrder));

        Position openPosition = buildOpenPosition();
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(openPosition));
        when(connectionManager.isConnected()).thenReturn(true);

        tracker.handleStatusUpdate(SL_ID, "Filled", Decimal.get(10), Decimal.ZERO, 140.00);

        verify(positionRepository).save(openPosition);
        assertThat(openPosition.getStatus()).isEqualTo(Position.PositionStatus.CLOSED);
        verify(connectionManager).cancelOrder(TP_ID);
        assertThat(siblingOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("STOP_LOSS_HIT");
    }

    // ─── Cancellation ────────────────────────────────────────────────────────

    @Test
    void handleStatusUpdate_parentCancelled_deregistersGroup() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleStatusUpdate(PARENT_ID, "Cancelled", Decimal.ZERO, Decimal.ZERO, 0.0);

        assertThat(tracker.getActiveGroupCount()).isEqualTo(0);
        assertThat(dbOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    // ─── Partial Fill ────────────────────────────────────────────────────────

    @Test
    void handleStatusUpdate_partiallyFilled_updatesFilledQty() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleStatusUpdate(PARENT_ID, "PartiallyFilled", Decimal.get(5), Decimal.get(5), 149.50);

        assertThat(dbOrder.getStatus()).isEqualTo(Order.OrderStatus.PARTIALLY_FILLED);
        assertThat(dbOrder.getFilledQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(dbOrder.getAvgFillPrice()).isEqualByComparingTo(BigDecimal.valueOf(149.50));
        verify(orderRepository).save(dbOrder);
    }

    // ─── Order Rejection ─────────────────────────────────────────────────────

    @Test
    void handleOrderRejection_knownOrder_updatesStatusToRejectedAndPublishesAlert() {
        Order dbOrder = buildDbOrder(PARENT_ID);
        when(orderRepository.findByIbOrderId(PARENT_ID)).thenReturn(Optional.of(dbOrder));

        tracker.handleOrderRejection(PARENT_ID, "Insufficient margin");

        assertThat(dbOrder.getStatus()).isEqualTo(Order.OrderStatus.REJECTED);
        verify(orderRepository).save(dbOrder);
        verify(orderEventPublisher).publishSystemAlert(eq("IBKR"), eq("ORDER_REJECTED"), anyString());
    }

    @Test
    void handleOrderRejection_unknownOrder_publishesAlertWithoutDbUpdate() {
        when(orderRepository.findByIbOrderId(999)).thenReturn(Optional.empty());

        tracker.handleOrderRejection(999, "Unknown order");

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher).publishSystemAlert(eq("IBKR"), eq("ORDER_REJECTED"), anyString());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OrderGroup buildOrderGroup() {
        return new OrderGroup(
                SIGNAL_ID, "AAPL", "NASDAQ", "STK", "BUY",
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(140.00), BigDecimal.valueOf(165.00),
                "FVG",
                PARENT_ID, TP_ID, SL_ID,
                PARENT_DB_ID, TP_DB_ID, SL_DB_ID,
                Instant.now()
        );
    }

    private Order buildDbOrder(int ibOrderId) {
        Order order = new Order();
        order.setIbOrderId(ibOrderId);
        order.setSymbol("AAPL");
        order.setQuantity(BigDecimal.valueOf(10));
        order.setStatus(Order.OrderStatus.SUBMITTED);
        return order;
    }

    private Position buildOpenPosition() {
        Position position = new Position();
        position.setSymbol("AAPL");
        position.setEntrySignalId(SIGNAL_ID);
        position.setQuantity(BigDecimal.valueOf(10));
        position.setEntryPrice(BigDecimal.valueOf(150.00));
        position.setSide(Order.OrderSide.BUY);
        position.setStatus(Position.PositionStatus.OPEN);
        return position;
    }
}
