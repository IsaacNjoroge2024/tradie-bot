package com.tradie.strategy.futures;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.service.OrderPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionRollerServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private RolloverService rolloverService;

    @Mock
    private OrderPublisher orderPublisher;

    private PositionRollerService service;

    @BeforeEach
    void setUp() {
        service = new PositionRollerService(positionRepository, rolloverService, orderPublisher);
    }

    // ─── rollPosition ─────────────────────────────────────────────────────────

    @Test
    void rollPosition_publishesRolloverAlert() {
        Position position = buildPosition("ESM5", "FUTURES");

        service.rollPosition(position, "ESU5");

        verify(orderPublisher).publishSystemAlert(
                eq("PositionRollerService"),
                eq("ROLLOVER_REQUIRED"),
                contains("ESU5"));
    }

    @Test
    void rollPosition_nullPosition_doesNotPublish() {
        service.rollPosition(null, "ESU5");

        verifyNoInteractions(orderPublisher);
    }

    @Test
    void rollPosition_blankNewContract_doesNotPublish() {
        service.rollPosition(buildPosition("ESM5", "FUTURES"), "");

        verifyNoInteractions(orderPublisher);
    }

    // ─── rollWithSpread ───────────────────────────────────────────────────────

    @Test
    void rollWithSpread_publishesSpreadRolloverAlert() {
        Position position = buildPosition("ESM5", "FUTURES");

        service.rollWithSpread(position, "ESU5", 5.50);

        verify(orderPublisher).publishSystemAlert(
                eq("PositionRollerService"),
                eq("SPREAD_ROLLOVER_REQUIRED"),
                contains("5.5"));
    }

    @Test
    void rollWithSpread_nullNewContract_doesNotPublish() {
        service.rollWithSpread(buildPosition("ESM5", "FUTURES"), null, 5.50);

        verifyNoInteractions(orderPublisher);
    }

    // ─── findPositionsDueToRoll ───────────────────────────────────────────────

    @Test
    void findPositionsDueToRoll_returnsFuturesPositionsPastRollDate() {
        Position futuresPos = buildPosition("ESM5", "FUTURES");
        Position stockPos   = buildPosition("AAPL", "STK");
        // shouldRoll is only called for FUTURES positions; STK is filtered out first
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(futuresPos, stockPos));
        when(rolloverService.shouldRoll("ESM5")).thenReturn(true);

        List<Position> result = service.findPositionsDueToRoll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("ESM5");
    }

    @Test
    void findPositionsDueToRoll_noPositionsDue_returnsEmpty() {
        Position futuresPos = buildPosition("ESM5", "FUTURES");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of(futuresPos));
        when(rolloverService.shouldRoll("ESM5")).thenReturn(false);

        assertThat(service.findPositionsDueToRoll()).isEmpty();
    }

    @Test
    void findPositionsDueToRoll_noOpenPositions_returnsEmpty() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN))
                .thenReturn(List.of());

        assertThat(service.findPositionsDueToRoll()).isEmpty();
        verifyNoInteractions(rolloverService);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildPosition(String symbol, String assetClass) {
        Position p = new Position();
        p.setSymbol(symbol);
        p.setExchange("CME");
        p.setAssetClass(assetClass);
        p.setSide(Order.OrderSide.BUY);
        p.setQuantity(BigDecimal.valueOf(2));
        p.setEntryPrice(BigDecimal.valueOf(5000));
        p.setStatus(Position.PositionStatus.OPEN);
        return p;
    }
}
