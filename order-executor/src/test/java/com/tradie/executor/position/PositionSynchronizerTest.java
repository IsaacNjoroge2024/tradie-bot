package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.IBPosition;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.PositionTracker;
import com.tradie.executor.order.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionSynchronizerTest {

    @Mock private PositionRepository positionRepository;
    @Mock private PositionTracker positionTracker;
    @Mock private OrderEventPublisher orderEventPublisher;
    @Mock private MarketDataService marketDataService;
    @Mock private IBConnectionManager connectionManager;

    private PositionSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new PositionSynchronizer(
                positionRepository, positionTracker, orderEventPublisher, marketDataService);
        synchronizer.setConnectionManager(connectionManager);
    }

    @Test
    void onPositionsLoaded_triggersSubscriptions() {
        when(positionTracker.getPositions()).thenReturn(Map.of());
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        synchronizer.onPositionsLoaded();

        verify(marketDataService).subscribeToOpenPositions();
    }

    @Test
    void syncPositions_dbOpenButIbkrMissing_marksAsClosed() {
        Position openPos = buildPosition("AAPL", "NASDAQ");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(openPos));
        when(positionTracker.getPositions()).thenReturn(Map.of());
        when(positionRepository.countByStatus(any())).thenReturn(0L);

        synchronizer.syncPositions();

        assertThat(openPos.getStatus()).isEqualTo(Position.PositionStatus.CLOSED);
        assertThat(openPos.getClosedAt()).isNotNull();
        verify(positionRepository).save(openPos);
    }

    @Test
    void syncPositions_ibkrPositionMissingInDb_createsNewPosition() {
        IBPosition ibPos = new IBPosition("AAPL", "NASDAQ", 10.0, 150.0, 1500.0, 0.0);
        when(positionTracker.getPositions()).thenReturn(Map.of("AAPL|NASDAQ", ibPos));
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());
        when(positionRepository.findByStatusAndSymbol(Position.PositionStatus.OPEN, "AAPL"))
                .thenReturn(List.of());
        when(positionRepository.countByStatus(any())).thenReturn(1L);

        synchronizer.syncPositions();

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(positionRepository).save(captor.capture());
        Position saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getEntryPrice()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(saved.getStatus()).isEqualTo(Position.PositionStatus.OPEN);
    }

    @Test
    void syncPositions_quantityMismatch_updatesDbQuantity() {
        IBPosition ibPos = new IBPosition("AAPL", "NASDAQ", 15.0, 150.0, 2250.0, 0.0);
        Position dbPos = buildPosition("AAPL", "NASDAQ");
        dbPos.setQuantity(BigDecimal.valueOf(10.0));

        when(positionTracker.getPositions()).thenReturn(Map.of("AAPL|NASDAQ", ibPos));
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(dbPos));
        when(positionRepository.findByStatusAndSymbol(Position.PositionStatus.OPEN, "AAPL"))
                .thenReturn(List.of(dbPos));
        when(positionRepository.countByStatus(any())).thenReturn(1L);

        synchronizer.syncPositions();

        assertThat(dbPos.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(15.0));
        verify(positionRepository).save(dbPos);
    }

    @Test
    void syncPositions_ibkrBlankExchange_doesNotFalselyCloseDbPosition() {
        // IBKR reports AAPL with a blank exchange; DB stores it with NASDAQ.
        // The symbol-based match should prevent the DB position from being wrongly closed.
        IBPosition ibPos = new IBPosition("AAPL", "", 10.0, 150.0, 1500.0, 0.0);
        Position dbPos = buildPosition("AAPL", "NASDAQ");

        when(positionTracker.getPositions()).thenReturn(Map.of("AAPL|", ibPos));
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(dbPos));
        when(positionRepository.findByStatusAndSymbol(Position.PositionStatus.OPEN, "AAPL"))
                .thenReturn(List.of(dbPos));
        when(positionRepository.countByStatus(any())).thenReturn(1L);

        synchronizer.syncPositions();

        assertThat(dbPos.getStatus()).isEqualTo(Position.PositionStatus.OPEN);
        verify(positionRepository, never()).save(argThat(p -> p.getStatus() == Position.PositionStatus.CLOSED));
    }

    @Test
    void syncPositions_publishesSyncEvent_whenDbPositionClosed() {
        Position openPos = buildPosition("TSLA", "NASDAQ");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(openPos));
        when(positionTracker.getPositions()).thenReturn(Map.of());
        when(positionRepository.countByStatus(any())).thenReturn(0L);

        synchronizer.syncPositions();

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventPublisher).publishOrderEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("POSITION_SYNC");
        assertThat(eventCaptor.getValue().symbol()).isEqualTo("TSLA");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildPosition(String symbol, String exchange) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setExchange(exchange);
        p.setAssetClass("STK");
        p.setSide(Order.OrderSide.BUY);
        p.setQuantity(BigDecimal.valueOf(10));
        p.setEntryPrice(BigDecimal.valueOf(150.0));
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }
}
