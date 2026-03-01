package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void defaultStatusIsPending() {
        Order order = new Order();
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void defaultFilledQuantityIsZero() {
        Order order = new Order();
        assertEquals(BigDecimal.ZERO, order.getFilledQuantity());
    }

    @Test
    void defaultIsBracketParentIsFalse() {
        Order order = new Order();
        assertFalse(order.getIsBracketParent());
    }

    @Test
    void createdAtIsNullBeforePersist() {
        Order order = new Order();
        assertNull(order.getCreatedAt());
    }

    @Test
    void onCreateSetsCreatedAt() {
        Order order = new Order();
        Instant before = Instant.now();
        order.onCreate();
        Instant after = Instant.now();

        assertNotNull(order.getCreatedAt());
        assertFalse(order.getCreatedAt().isBefore(before));
        assertFalse(order.getCreatedAt().isAfter(after));
    }

    @Test
    void onCreateDoesNotOverwriteExistingCreatedAt() {
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
        Order order = new Order();
        order.setCreatedAt(fixed);
        order.onCreate();

        assertEquals(fixed, order.getCreatedAt());
    }

    @Test
    void fieldAssignmentIsCorrect() {
        UUID signalId = UUID.randomUUID();
        Order order = new Order();
        order.setSignalId(signalId);
        order.setSymbol("AAPL");
        order.setExchange("NASDAQ");
        order.setAssetClass("STK");
        order.setSide(Order.OrderSide.BUY);
        order.setOrderType(Order.OrderType.LIMIT);
        order.setQuantity(new BigDecimal("100"));
        order.setLimitPrice(new BigDecimal("150.00"));
        order.setStopPrice(new BigDecimal("148.00"));

        assertEquals(signalId, order.getSignalId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals("NASDAQ", order.getExchange());
        assertEquals("STK", order.getAssetClass());
        assertEquals(Order.OrderSide.BUY, order.getSide());
        assertEquals(Order.OrderType.LIMIT, order.getOrderType());
        assertEquals(new BigDecimal("100"), order.getQuantity());
        assertEquals(new BigDecimal("150.00"), order.getLimitPrice());
        assertEquals(new BigDecimal("148.00"), order.getStopPrice());
    }

    @Test
    void bracketOrderFieldsAreCorrect() {
        UUID parentId = UUID.randomUUID();
        Order order = new Order();
        order.setParentOrderId(parentId);
        order.setIsBracketParent(true);

        assertEquals(parentId, order.getParentOrderId());
        assertTrue(order.getIsBracketParent());
    }

    @Test
    void fillDetailsAreCorrect() {
        Order order = new Order();
        Instant fillTime = Instant.now();
        order.setStatus(Order.OrderStatus.FILLED);
        order.setFilledQuantity(new BigDecimal("100"));
        order.setAvgFillPrice(new BigDecimal("150.25"));
        order.setCommission(new BigDecimal("1.50"));
        order.setFilledAt(fillTime);

        assertEquals(Order.OrderStatus.FILLED, order.getStatus());
        assertEquals(new BigDecimal("100"), order.getFilledQuantity());
        assertEquals(new BigDecimal("150.25"), order.getAvgFillPrice());
        assertEquals(new BigDecimal("1.50"), order.getCommission());
        assertEquals(fillTime, order.getFilledAt());
    }

    @Test
    void orderSideEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> Order.OrderSide.valueOf("BUY"));
        assertDoesNotThrow(() -> Order.OrderSide.valueOf("SELL"));
        assertEquals(2, Order.OrderSide.values().length);
    }

    @Test
    void orderTypeEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> Order.OrderType.valueOf("MARKET"));
        assertDoesNotThrow(() -> Order.OrderType.valueOf("LIMIT"));
        assertDoesNotThrow(() -> Order.OrderType.valueOf("STOP"));
        assertDoesNotThrow(() -> Order.OrderType.valueOf("STOP_LIMIT"));
        assertEquals(4, Order.OrderType.values().length);
    }

    @Test
    void orderStatusEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("PENDING"));
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("SUBMITTED"));
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("FILLED"));
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("PARTIALLY_FILLED"));
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("CANCELLED"));
        assertDoesNotThrow(() -> Order.OrderStatus.valueOf("REJECTED"));
        assertEquals(6, Order.OrderStatus.values().length);
    }
}
