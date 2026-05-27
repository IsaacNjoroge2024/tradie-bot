package com.tradie.executor.controller;

import com.tradie.common.entity.Order;
import com.tradie.common.repository.OrderRepository;
import com.tradie.executor.order.OrderCancellationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderCancellationService orderCancellationService;

    @Test
    void getRecentOrders_returnsOrderList() throws Exception {
        Order order = buildOrder(123, Order.OrderStatus.FILLED);
        when(orderRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ibOrderId").value(123))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void getActiveOrders_returnsSubmittedOrders() throws Exception {
        Order order = buildOrder(124, Order.OrderStatus.SUBMITTED);
        when(orderRepository.findByStatusIn(anyList())).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    void getOrder_existingId_returnsOrder() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(125, Order.OrderStatus.PENDING);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void getOrder_nonExistingId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_validOrder_sendsCancelRequest() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(200, Order.OrderStatus.SUBMITTED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isOk());

        verify(orderCancellationService).cancelOrder(200);
    }

    @Test
    void cancelOrder_noIbOrderId_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(null, Order.OrderStatus.SUBMITTED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderCancellationService);
    }

    @Test
    void cancelOrder_alreadyFilled_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(300, Order.OrderStatus.FILLED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderCancellationService);
    }

    @Test
    void cancelOrder_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isNotFound());
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Order buildOrder(Integer ibOrderId, Order.OrderStatus status) {
        Order order = new Order();
        order.setIbOrderId(ibOrderId);
        order.setSymbol("AAPL");
        order.setExchange("NASDAQ");
        order.setAssetClass("STK");
        order.setSide(Order.OrderSide.BUY);
        order.setOrderType(Order.OrderType.LIMIT);
        order.setQuantity(java.math.BigDecimal.TEN);
        order.setStatus(status);
        return order;
    }
}
