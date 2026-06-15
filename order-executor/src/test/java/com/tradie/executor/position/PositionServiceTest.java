package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.OrderIdManager;
import com.tradie.executor.order.OrderEventPublisher;
import com.tradie.executor.order.OrderGroup;
import com.tradie.executor.order.OrderStatusTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock private PositionRepository positionRepository;
    @Mock private IBConnectionManager connectionManager;
    @Mock private OrderStatusTracker orderStatusTracker;
    @Mock private OrderIdManager orderIdManager;
    @Mock private MarketDataService marketDataService;
    @Mock private PnLCalculator pnlCalculator;
    @Mock private OrderEventPublisher orderEventPublisher;

    private PositionService service;

    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID SIGNAL_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PositionService(positionRepository, connectionManager, orderStatusTracker,
                orderIdManager, marketDataService, pnlCalculator, orderEventPublisher);
    }

    // ─── getPosition ─────────────────────────────────────────────────────────

    @Test
    void getPosition_existingId_returnsPosition() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));

        Optional<Position> result = service.getPosition(POSITION_ID);

        assertThat(result).isPresent();
    }

    @Test
    void getPosition_unknownId_returnsEmpty() {
        when(positionRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.getPosition(UUID.randomUUID())).isEmpty();
    }

    // ─── closePosition ────────────────────────────────────────────────────────

    @Test
    void closePosition_submitsMarketOrderAndMarksAsClosing() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(true);
        when(orderIdManager.getNextOrderId()).thenReturn(200);
        when(orderStatusTracker.findGroupBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
        when(marketDataService.getLatestPrice("AAPL")).thenReturn(BigDecimal.valueOf(155.0));
        when(pnlCalculator.realizedPnl(any(), any())).thenReturn(BigDecimal.valueOf(50.0));

        service.closePosition(POSITION_ID, "Manual close");

        verify(connectionManager).placeOrder(eq(200), any(), any());
        // saved twice: once to mark CLOSING before side effects, once for exit price update
        verify(positionRepository, times(2)).save(pos);
        assertThat(pos.getStatus()).isEqualTo(Position.PositionStatus.CLOSING);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("POSITION_CLOSED_MANUAL");
    }

    @Test
    void closePosition_placeOrderFails_revertsToOpen() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(true);
        when(orderIdManager.getNextOrderId()).thenReturn(200);
        when(orderStatusTracker.findGroupBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("IBKR error")).when(connectionManager).placeOrder(anyInt(), any(), any());

        assertThatThrownBy(() -> service.closePosition(POSITION_ID, "Manual close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to submit close order");

        assertThat(pos.getStatus()).isEqualTo(Position.PositionStatus.OPEN);
        // saved twice: once to mark CLOSING, once to revert to OPEN on failure
        verify(positionRepository, times(2)).save(pos);
    }

    @Test
    void closePosition_placeOrderFailsAfterBracketsDeregistered_keepsClosingState() {
        // Brackets are cancelled first, then placeOrder throws. Reverting to OPEN would
        // leave the position with no TP/SL protection at IBKR, so we keep CLOSING.
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(true);
        when(orderIdManager.getNextOrderId()).thenReturn(200);
        when(orderStatusTracker.findGroupBySignalId(SIGNAL_ID)).thenReturn(Optional.of(buildOrderGroup()));
        doThrow(new RuntimeException("IBKR error")).when(connectionManager).placeOrder(anyInt(), any(), any());

        assertThatThrownBy(() -> service.closePosition(POSITION_ID, "Manual close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to submit close order");

        // Must NOT revert to OPEN — position would be unprotected at IBKR
        assertThat(pos.getStatus()).isEqualTo(Position.PositionStatus.CLOSING);
        // Only the initial CLOSING save — no revert save
        verify(positionRepository, times(1)).save(pos);
        // Both bracket orders must have been cancelled
        verify(connectionManager, times(2)).cancelOrder(anyInt());
    }

    @Test
    void closePosition_closingPosition_throwsIllegalState() {
        Position pos = buildOpenPosition();
        pos.setStatus(Position.PositionStatus.CLOSING);
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));

        assertThatThrownBy(() -> service.closePosition(POSITION_ID, "Retry close"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    void closePosition_notFound_throwsIllegalArgument() {
        when(positionRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closePosition(UUID.randomUUID(), "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closePosition_alreadyClosed_throwsIllegalState() {
        Position pos = buildOpenPosition();
        pos.setStatus(Position.PositionStatus.CLOSED);
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));

        assertThatThrownBy(() -> service.closePosition(POSITION_ID, "reason"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closePosition_notConnected_throwsIllegalState() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(false);

        assertThatThrownBy(() -> service.closePosition(POSITION_ID, "reason"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── modifyStop ───────────────────────────────────────────────────────────

    @Test
    void modifyStop_updatesStopInDbAndIbkr() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(true);
        when(orderStatusTracker.findGroupBySignalId(SIGNAL_ID)).thenReturn(Optional.of(buildOrderGroup()));

        service.modifyStop(POSITION_ID, BigDecimal.valueOf(142.0));

        assertThat(pos.getStopLoss()).isEqualByComparingTo(BigDecimal.valueOf(142.0));
        verify(positionRepository).save(pos);
        verify(connectionManager).placeOrder(anyInt(), any(), any());
    }

    @Test
    void modifyStop_notConnected_throwsIllegalState() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(false);

        assertThatThrownBy(() -> service.modifyStop(POSITION_ID, BigDecimal.valueOf(140.0)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── modifyTarget ─────────────────────────────────────────────────────────

    @Test
    void modifyTarget_updatesTargetInDbAndIbkr() {
        Position pos = buildOpenPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(pos));
        when(connectionManager.isConnected()).thenReturn(true);
        when(orderStatusTracker.findGroupBySignalId(SIGNAL_ID)).thenReturn(Optional.of(buildOrderGroup()));

        service.modifyTarget(POSITION_ID, BigDecimal.valueOf(170.0));

        assertThat(pos.getTakeProfit()).isEqualByComparingTo(BigDecimal.valueOf(170.0));
        verify(positionRepository).save(pos);
        verify(connectionManager).placeOrder(anyInt(), any(), any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Position buildOpenPosition() {
        Position p = new Position();
        p.setSymbol("AAPL");
        p.setExchange("NASDAQ");
        p.setAssetClass("STK");
        p.setSide(Order.OrderSide.BUY);
        p.setEntryPrice(BigDecimal.valueOf(150.0));
        p.setQuantity(BigDecimal.valueOf(10));
        p.setStopLoss(BigDecimal.valueOf(140.0));
        p.setTakeProfit(BigDecimal.valueOf(165.0));
        p.setCommissionTotal(BigDecimal.ZERO);
        p.setEntrySignalId(SIGNAL_ID);
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }

    private OrderGroup buildOrderGroup() {
        return new OrderGroup(
                SIGNAL_ID, "AAPL", "NASDAQ", "STK", "BUY",
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(140.0), BigDecimal.valueOf(165.0),
                "FVG", 100, 101, 102,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now()
        );
    }
}
