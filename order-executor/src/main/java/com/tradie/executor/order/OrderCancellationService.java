package com.tradie.executor.order;

import com.tradie.common.entity.Order;
import com.tradie.common.repository.OrderRepository;
import com.tradie.executor.ibkr.IBConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles order cancellation requests.
 *
 * <p>Cancellation scenarios:
 * <ul>
 *   <li>Manual cancel via the REST API (e.g., from Telegram bot)</li>
 *   <li>Signal expires before fill</li>
 *   <li>Emergency cancel-all when a risk limit is breached</li>
 * </ul>
 *
 * <p>When the parent bracket order is cancelled, IBKR automatically cancels the
 * child (TP and SL) orders.
 */
@Service
public class OrderCancellationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);

    private final IBConnectionManager connectionManager;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OrderCancellationService(IBConnectionManager connectionManager,
                                    OrderRepository orderRepository,
                                    OrderEventPublisher orderEventPublisher) {
        this.connectionManager = connectionManager;
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    /**
     * Cancels a single order by its IBKR order ID.
     *
     * @param ibOrderId the IBKR order ID to cancel
     * @throws IllegalStateException if not connected to IBKR
     */
    public void cancelOrder(int ibOrderId) {
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Cannot cancel order: not connected to IBKR");
        }
        log.info("Cancelling order: ibOrderId={}", ibOrderId);
        connectionManager.cancelOrder(ibOrderId);
    }

    /**
     * Cancels all orders currently in PENDING, SUBMITTED, or PARTIALLY_FILLED status.
     * Used as an emergency stop when risk limits are breached.
     */
    public void cancelAllActiveOrders() {
        List<Order> activeOrders = orderRepository.findByStatusIn(List.of(
                Order.OrderStatus.PENDING,
                Order.OrderStatus.SUBMITTED,
                Order.OrderStatus.PARTIALLY_FILLED
        ));

        if (activeOrders.isEmpty()) {
            log.info("No active orders to cancel");
            return;
        }

        log.warn("Emergency cancel-all: cancelling {} active orders", activeOrders.size());
        orderEventPublisher.publishSystemAlert("Order Executor", "RISK_LIMIT_BREACHED",
                "Risk limit breached! Emergency cancel-all triggered for " + activeOrders.size() + " active order(s).");
        int failures = 0;
        for (Order order : activeOrders) {
            if (order.getIbOrderId() != null) {
                try {
                    cancelOrder(order.getIbOrderId());
                } catch (Exception e) {
                    failures++;
                    log.error("Failed to cancel ibOrderId={}", order.getIbOrderId(), e);
                }
            }
        }
        if (failures > 0) {
            throw new IllegalStateException("Cancel-all completed with " + failures + " failure(s)");
        }
    }
}
