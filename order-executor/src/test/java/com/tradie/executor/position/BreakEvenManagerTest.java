package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.config.PositionProperties;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.order.OrderEventPublisher;
import com.tradie.executor.order.OrderStatusTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BreakEvenManagerTest {

    @Mock private PositionRepository positionRepository;
    @Mock private OrderStatusTracker orderStatusTracker;
    @Mock private OrderEventPublisher orderEventPublisher;
    @Mock private IBConnectionManager connectionManager;

    private BreakEvenManager manager;
    private PositionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PositionProperties();
        // defaults: enabled=true, activationPct=1.0
        manager = new BreakEvenManager(properties, positionRepository, orderStatusTracker, orderEventPublisher);
        manager.setConnectionManager(connectionManager);
    }

    @Test
    void checkAndActivate_disabled_doesNothing() {
        properties.getBreakEven().setEnabled(false);
        Position position = buildPosition(100.0, 5.0);

        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void checkAndActivate_belowThreshold_doesNothing() {
        Position position = buildPosition(100.0, 5.0);

        // 0.5% gain — below 1.0% threshold
        manager.checkAndActivate(position, BigDecimal.valueOf(100.5));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void checkAndActivate_atThreshold_activatesBreakEven() {
        UUID signalId = UUID.randomUUID();
        Position position = buildPosition(100.0, 5.0);
        position.setEntrySignalId(signalId);
        position.setCommissionTotal(BigDecimal.valueOf(2.0)); // 0.2 per share
        when(orderStatusTracker.findGroupBySignalId(signalId)).thenReturn(Optional.empty());

        // 2% gain → above 1% threshold
        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));

        // break-even = 100 + (2/10) = 100.2
        assertThat(position.getStopLoss()).isGreaterThanOrEqualTo(BigDecimal.valueOf(100.0));
        verify(positionRepository).save(position);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("BREAK_EVEN_ACTIVATED");
    }

    @Test
    void checkAndActivate_alreadyActivated_isIdempotent() {
        UUID signalId = UUID.randomUUID();
        Position position = buildPosition(100.0, 5.0);
        position.setEntrySignalId(signalId);
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());

        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));
        reset(positionRepository, orderEventPublisher);

        // Second call should not activate again
        manager.checkAndActivate(position, BigDecimal.valueOf(103.0));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void checkAndActivate_closedPosition_isIgnored() {
        Position position = buildPosition(100.0, 5.0);
        position.setStatus(Position.PositionStatus.CLOSED);

        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void clearActivated_removesPositionFromActivatedSet() {
        UUID signalId = UUID.randomUUID();
        Position position = buildPosition(100.0, 5.0);
        position.setEntrySignalId(signalId);
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());

        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));
        verify(positionRepository).save(position);

        // Reset stop so break-even guard doesn't prevent a second activation
        position.setStopLoss(BigDecimal.valueOf(5.0));
        manager.clearActivated(position.getId());
        reset(positionRepository, orderEventPublisher);
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());

        manager.checkAndActivate(position, BigDecimal.valueOf(102.0));

        verify(positionRepository).save(position);
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("BREAK_EVEN_ACTIVATED");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildPosition(double entry, double stopLoss) {
        Position p = new Position();
        try {
            // Inject a UUID via reflection since @GeneratedValue only fires on persist
            java.lang.reflect.Field idField = Position.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(p, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        p.setSymbol("AAPL");
        p.setExchange("NASDAQ");
        p.setAssetClass("STK");
        p.setSide(Order.OrderSide.BUY);
        p.setEntryPrice(BigDecimal.valueOf(entry));
        p.setQuantity(BigDecimal.valueOf(10));
        p.setStopLoss(BigDecimal.valueOf(stopLoss));
        p.setCommissionTotal(BigDecimal.ZERO);
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }
}
