package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    void defaultStatusIsOpen() {
        Position position = new Position();
        assertEquals(Position.PositionStatus.OPEN, position.getStatus());
    }

    @Test
    void defaultCommissionTotalIsZero() {
        Position position = new Position();
        assertEquals(0.0, position.getCommissionTotal());
    }

    @Test
    void defaultOpenedAtIsSet() {
        Instant before = Instant.now();
        Position position = new Position();
        Instant after = Instant.now();

        assertNotNull(position.getOpenedAt());
        assertFalse(position.getOpenedAt().isBefore(before));
        assertFalse(position.getOpenedAt().isAfter(after));
    }

    @Test
    void fieldAssignmentIsCorrect() {
        UUID entrySignalId = UUID.randomUUID();
        Position position = new Position();
        position.setSymbol("EURUSD");
        position.setExchange("IDEALPRO");
        position.setAssetClass("CASH");
        position.setSide(Order.OrderSide.BUY);
        position.setQuantity(100000.0);
        position.setEntryPrice(1.0850);
        position.setStopLoss(1.0800);
        position.setTakeProfit(1.0950);
        position.setStrategy("ORDER_BLOCK");
        position.setEntrySignalId(entrySignalId);

        assertEquals("EURUSD", position.getSymbol());
        assertEquals("IDEALPRO", position.getExchange());
        assertEquals("CASH", position.getAssetClass());
        assertEquals(Order.OrderSide.BUY, position.getSide());
        assertEquals(100000.0, position.getQuantity());
        assertEquals(1.0850, position.getEntryPrice());
        assertEquals(1.0800, position.getStopLoss());
        assertEquals(1.0950, position.getTakeProfit());
        assertEquals("ORDER_BLOCK", position.getStrategy());
        assertEquals(entrySignalId, position.getEntrySignalId());
    }

    @Test
    void closingPositionSetsCorrectFields() {
        UUID exitSignalId = UUID.randomUUID();
        Instant closedAt = Instant.now();

        Position position = new Position();
        position.setStatus(Position.PositionStatus.CLOSED);
        position.setExitPrice(1.0950);
        position.setRealizedPnl(1000.0);
        position.setClosedAt(closedAt);
        position.setExitSignalId(exitSignalId);

        assertEquals(Position.PositionStatus.CLOSED, position.getStatus());
        assertEquals(1.0950, position.getExitPrice());
        assertEquals(1000.0, position.getRealizedPnl());
        assertEquals(closedAt, position.getClosedAt());
        assertEquals(exitSignalId, position.getExitSignalId());
    }

    @Test
    void unrealizedPnlCanBeUpdated() {
        Position position = new Position();
        position.setUnrealizedPnl(250.50);
        assertEquals(250.50, position.getUnrealizedPnl());
    }

    @Test
    void trailingStopCanBeSet() {
        Position position = new Position();
        position.setTrailingStopPct(1.5);
        assertEquals(1.5, position.getTrailingStopPct());
    }

    @Test
    void positionStatusEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> Position.PositionStatus.valueOf("OPEN"));
        assertDoesNotThrow(() -> Position.PositionStatus.valueOf("CLOSED"));
        assertEquals(2, Position.PositionStatus.values().length);
    }

    @Test
    void closedAtIsNullByDefault() {
        Position position = new Position();
        assertNull(position.getClosedAt());
    }
}
