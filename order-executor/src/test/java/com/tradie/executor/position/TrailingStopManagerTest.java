package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.config.PositionProperties;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrailingStopManagerTest {

    @Mock private PositionRepository positionRepository;
    @Mock private OrderStatusTracker orderStatusTracker;
    @Mock private OrderEventPublisher orderEventPublisher;
    @Mock private IBConnectionManager connectionManager;

    private TrailingStopManager manager;
    private PositionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PositionProperties();
        // defaults: enabled=true, defaultTrailPct=2.0, activationPct=1.0, stepPct=0.25
        manager = new TrailingStopManager(properties, positionRepository, orderStatusTracker, orderEventPublisher);
        manager.setConnectionManager(connectionManager);
    }

    @Test
    void checkAndAdjust_trailingStopDisabled_doesNothing() {
        properties.getTrailingStop().setEnabled(false);
        Position position = buildLongPosition(100.0, 2.0);

        manager.checkAndAdjust(position, BigDecimal.valueOf(102.0));

        verifyNoInteractions(positionRepository, orderEventPublisher);
    }

    @Test
    void checkAndAdjust_belowActivationPct_doesNothing() {
        Position position = buildLongPosition(100.0, 2.0);
        // 0.5% gain — below 1.0% activation threshold
        manager.checkAndAdjust(position, BigDecimal.valueOf(100.5));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void checkAndAdjust_longPosition_updatesStopWhenPriceRises() {
        UUID signalId = UUID.randomUUID();
        Position position = buildLongPosition(100.0, 2.0);
        position.setEntrySignalId(signalId);
        position.setStopLoss(BigDecimal.valueOf(98.0));

        when(orderStatusTracker.findGroupBySignalId(signalId)).thenReturn(Optional.empty());

        // Price at 105 → 5% gain; trail 2% → new stop = 105 * 0.98 = 102.9
        // step = 100 * 0.25/100 = 0.25; 102.9 - 98.0 = 4.9 > 0.25 → should adjust
        manager.checkAndAdjust(position, BigDecimal.valueOf(105.0));

        verify(positionRepository).save(position);
        assertThat(position.getStopLoss()).isGreaterThan(BigDecimal.valueOf(98.0));

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("TRAILING_STOP_MOVED");
    }

    @Test
    void checkAndAdjust_longPosition_priceDoesNotRise_noAdjustment() {
        Position position = buildLongPosition(100.0, 2.0);
        position.setStopLoss(BigDecimal.valueOf(98.0));
        // First call sets the hwm at 105
        UUID signalId = UUID.randomUUID();
        position.setEntrySignalId(signalId);
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());
        manager.checkAndAdjust(position, BigDecimal.valueOf(105.0));
        reset(positionRepository, orderEventPublisher);

        // Price falls to 103 — HWM is still 105, no adjustment needed
        manager.checkAndAdjust(position, BigDecimal.valueOf(103.0));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void checkAndAdjust_closedPosition_isIgnored() {
        Position position = buildLongPosition(100.0, 2.0);
        position.setStatus(Position.PositionStatus.CLOSED);

        manager.checkAndAdjust(position, BigDecimal.valueOf(105.0));

        verifyNoInteractions(positionRepository);
    }

    @Test
    void clearHighWaterMark_allowsReEvaluation() {
        Position position = buildLongPosition(100.0, 2.0);
        position.setEntrySignalId(UUID.randomUUID());
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());

        // First call: establish HWM at 105, triggers stop adjustment
        manager.checkAndAdjust(position, BigDecimal.valueOf(105.0));
        verify(positionRepository).save(position);

        // Clear the HWM
        manager.clearHighWaterMark(position.getId());

        // Reset stop so the next check can trigger an adjustment again
        position.setStopLoss(BigDecimal.valueOf(98.0));
        reset(positionRepository, orderEventPublisher);
        when(orderStatusTracker.findGroupBySignalId(any())).thenReturn(Optional.empty());

        // After clearing, a new higher price can re-establish the HWM and trigger adjustment
        manager.checkAndAdjust(position, BigDecimal.valueOf(107.0));

        verify(positionRepository).save(position);
        assertThat(position.getStopLoss()).isGreaterThan(BigDecimal.valueOf(98.0));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildLongPosition(double entry, double trailPct) {
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
        p.setTrailingStopPct(trailPct);
        p.setStopLoss(BigDecimal.valueOf(entry * 0.98));
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }
}
