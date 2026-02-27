package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

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
        assertEquals(0.0, order.getFilledQuantity());
    }

    @Test
    void defaultIsBracketParentIsFalse() {
        Order order = new Order();
        assertFalse(order.getIsBracketParent());
    }

    @Test
    void defaultCreatedAtIsSet() {
        Instant before = Instant.now();
        Order order = new Order();
        Instant after = Instant.now();

        assertNotNull(order.getCreatedAt());
        assertFalse(order.getCreatedAt().isBefore(before));
        assertFalse(order.getCreatedAt().isAfter(after));
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
        order.setQuantity(100.0);
        order.setLimitPrice(150.00);
        order.setStopPrice(148.00);

        assertEquals(signalId, order.getSignalId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals("NASDAQ", order.getExchange());
        assertEquals("STK", order.getAssetClass());
        assertEquals(Order.OrderSide.BUY, order.getSide());
        assertEquals(Order.OrderType.LIMIT, order.getOrderType());
        assertEquals(100.0, order.getQuantity());
        assertEquals(150.00, order.getLimitPrice());
        assertEquals(148.00, order.getStopPrice());
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
        order.setFilledQuantity(100.0);
        order.setAvgFillPrice(150.25);
        order.setCommission(1.50);
        order.setFilledAt(fillTime);

        assertEquals(Order.OrderStatus.FILLED, order.getStatus());
        assertEquals(100.0, order.getFilledQuantity());
        assertEquals(150.25, order.getAvgFillPrice());
        assertEquals(1.50, order.getCommission());
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
