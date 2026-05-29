package com.tradie.executor.controller;

import com.tradie.common.entity.Order;
import com.tradie.common.repository.OrderRepository;
import com.tradie.executor.order.OrderCancellationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for order management and manual intervention.
 *
 * <pre>
 * GET  /api/orders          – list the 20 most recent orders
 * GET  /api/orders/active   – currently open (non-final) orders
 * GET  /api/orders/{id}     – order details by database UUID
 * POST /api/orders/{id}/cancel – request cancellation of an order
 * </pre>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final OrderCancellationService orderCancellationService;

    public OrderController(OrderRepository orderRepository,
                           OrderCancellationService orderCancellationService) {
        this.orderRepository = orderRepository;
        this.orderCancellationService = orderCancellationService;
    }

    /** Returns the 20 most recently created orders. */
    @GetMapping
    public List<Order> getRecentOrders() {
        return orderRepository.findTop20ByOrderByCreatedAtDesc();
    }

    /** Returns all orders in PENDING, SUBMITTED, or PARTIALLY_FILLED status. */
    @GetMapping("/active")
    public List<Order> getActiveOrders() {
        return orderRepository.findByStatusIn(List.of(
                Order.OrderStatus.PENDING,
                Order.OrderStatus.SUBMITTED,
                Order.OrderStatus.PARTIALLY_FILLED
        ));
    }

    /** Returns order details for a given database UUID. */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Requests cancellation of an order. The order must have an IBKR order ID assigned
     * and must not already be in a final state (FILLED, CANCELLED, REJECTED).
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable UUID id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (order.getIbOrderId() == null) {
            return ResponseEntity.badRequest().body("Order has no IBKR order ID assigned");
        }
        if (isFinalStatus(order.getStatus())) {
            return ResponseEntity.badRequest()
                    .body("Order is already in final status: " + order.getStatus());
        }

        log.info("Manual cancel requested for orderId={} ibOrderId={}", id, order.getIbOrderId());
        try {
            orderCancellationService.cancelOrder(order.getIbOrderId());
            return ResponseEntity.ok("Cancel request sent for ibOrderId=" + order.getIbOrderId());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Cancel request failed for orderId={} ibOrderId={}", id, order.getIbOrderId(), e);
            return ResponseEntity.internalServerError().body("Failed to submit cancel request to broker");
        }
    }

    private boolean isFinalStatus(Order.OrderStatus status) {
        return status == Order.OrderStatus.FILLED
                || status == Order.OrderStatus.CANCELLED
                || status == Order.OrderStatus.REJECTED;
    }
}
