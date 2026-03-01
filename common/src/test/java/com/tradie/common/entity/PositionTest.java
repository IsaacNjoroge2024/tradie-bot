package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertEquals(BigDecimal.ZERO, position.getCommissionTotal());
    }

    @Test
    void openedAtIsNullBeforePersist() {
        Position position = new Position();
        assertNull(position.getOpenedAt());
    }

    @Test
    void onOpenSetsOpenedAt() {
        Position position = new Position();
        Instant before = Instant.now();
        position.onOpen();
        Instant after = Instant.now();

        assertNotNull(position.getOpenedAt());
        assertFalse(position.getOpenedAt().isBefore(before));
        assertFalse(position.getOpenedAt().isAfter(after));
    }

    @Test
    void onOpenDoesNotOverwriteExistingOpenedAt() {
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
        Position position = new Position();
        position.setOpenedAt(fixed);
        position.onOpen();

        assertEquals(fixed, position.getOpenedAt());
    }

    @Test
    void fieldAssignmentIsCorrect() {
        UUID entrySignalId = UUID.randomUUID();
        Position position = new Position();
        position.setSymbol("EURUSD");
        position.setExchange("IDEALPRO");
        position.setAssetClass("CASH");
        position.setSide(Order.OrderSide.BUY);
        position.setQuantity(new BigDecimal("100000"));
        position.setEntryPrice(new BigDecimal("1.0850"));
        position.setStopLoss(new BigDecimal("1.0800"));
        position.setTakeProfit(new BigDecimal("1.0950"));
        position.setStrategy("ORDER_BLOCK");
        position.setEntrySignalId(entrySignalId);

        assertEquals("EURUSD", position.getSymbol());
        assertEquals("IDEALPRO", position.getExchange());
        assertEquals("CASH", position.getAssetClass());
        assertEquals(Order.OrderSide.BUY, position.getSide());
        assertEquals(new BigDecimal("100000"), position.getQuantity());
        assertEquals(new BigDecimal("1.0850"), position.getEntryPrice());
        assertEquals(new BigDecimal("1.0800"), position.getStopLoss());
        assertEquals(new BigDecimal("1.0950"), position.getTakeProfit());
        assertEquals("ORDER_BLOCK", position.getStrategy());
        assertEquals(entrySignalId, position.getEntrySignalId());
    }

    @Test
    void closingPositionSetsCorrectFields() {
        UUID exitSignalId = UUID.randomUUID();
        Instant closedAt = Instant.now();

        Position position = new Position();
        position.setStatus(Position.PositionStatus.CLOSED);
        position.setExitPrice(new BigDecimal("1.0950"));
        position.setRealizedPnl(new BigDecimal("1000.00"));
        position.setClosedAt(closedAt);
        position.setExitSignalId(exitSignalId);

        assertEquals(Position.PositionStatus.CLOSED, position.getStatus());
        assertEquals(new BigDecimal("1.0950"), position.getExitPrice());
        assertEquals(new BigDecimal("1000.00"), position.getRealizedPnl());
        assertEquals(closedAt, position.getClosedAt());
        assertEquals(exitSignalId, position.getExitSignalId());
    }

    @Test
    void unrealizedPnlCanBeUpdated() {
        Position position = new Position();
        position.setUnrealizedPnl(new BigDecimal("250.50"));
        assertEquals(new BigDecimal("250.50"), position.getUnrealizedPnl());
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
