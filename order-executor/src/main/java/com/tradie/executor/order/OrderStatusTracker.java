package com.tradie.executor.order;

import com.ib.client.Decimal;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks active bracket order groups and handles IBKR order status callbacks.
 *
 * <p>Each bracket order (parent + TP + SL) registers all three IBKR order IDs
 * as keys in an in-memory map pointing to the same {@link OrderGroup}. This allows
 * O(1) lookup regardless of which order ID receives the status update.
 *
 * <p>{@link #setConnectionManager} is called lazily by {@link IBConnectionManager}
 * during startup to avoid a circular Spring dependency.
 */
@Service
public class OrderStatusTracker {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusTracker.class);

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final OrderEventPublisher orderEventPublisher;

    // ibkrOrderId → OrderGroup; all three IDs of a bracket map to the same group
    private final ConcurrentHashMap<Integer, OrderGroup> activeGroups = new ConcurrentHashMap<>();

    // Injected lazily to avoid circular dependency with IBConnectionManager
    private volatile IBConnectionManager connectionManager;

    public OrderStatusTracker(
            OrderRepository orderRepository,
            PositionRepository positionRepository,
            OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    /** Called by IBConnectionManager during init to wire the back-reference. */
    public void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a newly submitted bracket group so status callbacks can be routed.
     * All three IBKR order IDs are stored as separate keys for fast lookup.
     */
    public void registerOrderGroup(OrderGroup group) {
        activeGroups.put(group.parentIbOrderId(), group);
        activeGroups.put(group.takeProfitIbOrderId(), group);
        activeGroups.put(group.stopLossIbOrderId(), group);
        log.info("Bracket group registered: signal={} symbol={} parent={} tp={} sl={}",
                group.signalId(), group.symbol(),
                group.parentIbOrderId(), group.takeProfitIbOrderId(), group.stopLossIbOrderId());
    }

    // ─── Status Handling ──────────────────────────────────────────────────────

    /**
     * Entry point called by {@link com.tradie.executor.ibkr.TradieEWrapper#orderStatus}.
     * Routes the status update to the appropriate handler based on IBKR status string.
     */
    public void handleStatusUpdate(int orderId, String status, Decimal filled,
                                   Decimal remaining, double avgFillPrice) {
        OrderGroup group = activeGroups.get(orderId);
        if (group == null) {
            log.debug("No active bracket group for ibOrderId={}, skipping", orderId);
            return;
        }

        Order dbOrder = orderRepository.findByIbOrderId(orderId).orElse(null);
        if (dbOrder == null) {
            log.warn("DB order not found for ibOrderId={}", orderId);
            return;
        }

        log.info("Order status update: ibOrderId={} status={} filled={} avgFillPrice={}",
                orderId, status, filled, avgFillPrice);

        switch (status) {
            case "Filled"          -> handleFill(orderId, group, dbOrder, filled, avgFillPrice);
            case "PartiallyFilled" -> handlePartialFill(dbOrder, filled, avgFillPrice);
            case "Submitted", "PreSubmitted" -> {
                dbOrder.setStatus(Order.OrderStatus.SUBMITTED);
                orderRepository.save(dbOrder);
            }
            case "Cancelled", "ApiCancelled" -> handleCancellation(orderId, group, dbOrder);
            default -> log.debug("Unhandled IBKR order status: ibOrderId={} status={}", orderId, status);
        }
    }

    // ─── Private Handlers ─────────────────────────────────────────────────────

    private void handleFill(int orderId, OrderGroup group, Order dbOrder,
                            Decimal filled, double avgFillPrice) {
        dbOrder.setStatus(Order.OrderStatus.FILLED);
        dbOrder.setFilledQuantity(BigDecimal.valueOf(filled.value().doubleValue()));
        dbOrder.setAvgFillPrice(BigDecimal.valueOf(avgFillPrice));
        dbOrder.setFilledAt(Instant.now());
        orderRepository.save(dbOrder);

        boolean isParent = orderId == group.parentIbOrderId();
        boolean isTP     = orderId == group.takeProfitIbOrderId();
        boolean isSL     = orderId == group.stopLossIbOrderId();

        if (isParent) {
            handleParentFill(group, avgFillPrice);
        } else if (isTP || isSL) {
            handleChildFill(group, isTP, avgFillPrice);
        }
    }

    private void handleParentFill(OrderGroup group, double avgFillPrice) {
        // Create position record
        Position position = buildPosition(group, avgFillPrice);
        positionRepository.save(position);
        log.info("Position opened: signal={} symbol={} entry={}", group.signalId(), group.symbol(), avgFillPrice);

        orderEventPublisher.publishOrderEvent(new OrderEvent(
                "FILLED",
                group.signalId(),
                group.symbol(),
                group.side(),
                group.quantity().doubleValue(),
                avgFillPrice,
                group.strategy(),
                "Entry order filled at " + avgFillPrice,
                Instant.now(),
                group.stopLoss() != null ? group.stopLoss().doubleValue() : null,
                group.takeProfit() != null ? group.takeProfit().doubleValue() : null,
                null
        ));
    }

    private void handleChildFill(OrderGroup group, boolean isTP, double avgFillPrice) {
        // Capture entry price from the open position before closing it
        AtomicReference<Double> capturedEntryPrice = new AtomicReference<>(null);

        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        openPositions.stream()
                .filter(p -> p.getSymbol().equals(group.symbol())
                          && group.signalId().equals(p.getEntrySignalId()))
                .findFirst()
                .ifPresent(position -> {
                    capturedEntryPrice.set(position.getEntryPrice().doubleValue());
                    BigDecimal exitPrice = BigDecimal.valueOf(avgFillPrice);
                    position.setExitPrice(exitPrice);
                    position.setClosedAt(Instant.now());
                    position.setStatus(Position.PositionStatus.CLOSED);
                    position.setRealizedPnl(calculatePnl(position.getEntryPrice(), group.side(), group.quantity(), exitPrice));
                    positionRepository.save(position);
                    log.info("Position closed: signal={} symbol={} exit={} pnl={}",
                            group.signalId(), group.symbol(), avgFillPrice, position.getRealizedPnl());
                });

        // Cancel the sibling order
        int siblingId = isTP ? group.stopLossIbOrderId() : group.takeProfitIbOrderId();
        if (connectionManager != null && connectionManager.isConnected()) {
            connectionManager.cancelOrder(siblingId);
            log.info("Sibling order cancelled: ibOrderId={}", siblingId);
        }
        // Persist the sibling's terminal state now; its async cancel callback will be
        // skipped once the group is deregistered below (activeGroups.get returns null).
        orderRepository.findByIbOrderId(siblingId).ifPresent(sibling -> {
            sibling.setStatus(Order.OrderStatus.CANCELLED);
            sibling.setCancelledAt(Instant.now());
            orderRepository.save(sibling);
        });

        // Remove all three from active tracking
        deregisterOrderGroup(group);

        String eventType = isTP ? "TAKE_PROFIT_HIT" : "STOP_LOSS_HIT";
        orderEventPublisher.publishOrderEvent(new OrderEvent(
                eventType,
                group.signalId(),
                group.symbol(),
                group.side(),
                group.quantity().doubleValue(),
                avgFillPrice,
                group.strategy(),
                "Position closed via " + (isTP ? "take profit" : "stop loss") + " at " + avgFillPrice,
                Instant.now(),
                null,
                null,
                capturedEntryPrice.get()
        ));
    }

    private void handlePartialFill(Order dbOrder, Decimal filled, double avgFillPrice) {
        dbOrder.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
        dbOrder.setFilledQuantity(BigDecimal.valueOf(filled.value().doubleValue()));
        dbOrder.setAvgFillPrice(BigDecimal.valueOf(avgFillPrice));
        orderRepository.save(dbOrder);
        log.info("Order partially filled: ibOrderId={} filled={} avgPrice={}",
                dbOrder.getIbOrderId(), filled, avgFillPrice);
    }

    private void handleCancellation(int orderId, OrderGroup group, Order dbOrder) {
        dbOrder.setStatus(Order.OrderStatus.CANCELLED);
        dbOrder.setCancelledAt(Instant.now());
        orderRepository.save(dbOrder);

        // Deregister the whole bracket group on any terminal cancellation to prevent stale routing
        deregisterOrderGroup(group);
        log.info("Bracket group deregistered after cancellation: signal={} ibOrderId={}", group.signalId(), orderId);
    }

    public void deregisterOrderGroup(OrderGroup group) {
        activeGroups.remove(group.parentIbOrderId());
        activeGroups.remove(group.takeProfitIbOrderId());
        activeGroups.remove(group.stopLossIbOrderId());
    }

    /**
     * Finds the active bracket group associated with a given signal ID.
     * Used by TrailingStopManager and BreakEvenManager to look up IBKR order IDs.
     */
    public Optional<OrderGroup> findGroupBySignalId(UUID signalId) {
        if (signalId == null) return Optional.empty();
        return activeGroups.values().stream()
                .filter(g -> signalId.equals(g.signalId()))
                .findFirst();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Position buildPosition(OrderGroup group, double entryPrice) {
        Position position = new Position();
        position.setSymbol(group.symbol());
        position.setExchange(group.exchange());
        position.setAssetClass(group.assetClass());
        position.setSide(Order.OrderSide.valueOf(group.side()));
        position.setQuantity(group.quantity());
        position.setEntryPrice(BigDecimal.valueOf(entryPrice));
        position.setStopLoss(group.stopLoss());
        position.setTakeProfit(group.takeProfit());
        position.setStrategy(group.strategy());
        position.setEntrySignalId(group.signalId());
        position.setStatus(Position.PositionStatus.OPEN);
        return position;
    }

    private BigDecimal calculatePnl(BigDecimal entryPrice, String side, BigDecimal quantity, BigDecimal exitPrice) {
        if ("BUY".equals(side)) {
            return exitPrice.subtract(entryPrice).multiply(quantity);
        } else {
            return entryPrice.subtract(exitPrice).multiply(quantity);
        }
    }

    /** Returns the number of currently tracked bracket groups (for monitoring). */
    public int getActiveGroupCount() {
        // Each group is registered under 3 keys; divide to get unique group count
        return activeGroups.size() / 3;
    }

    /**
     * Handles order rejections reported by IBKR error callbacks.
     * Updates the status of the order in the database to REJECTED and sends a system alert.
     */
    public void handleOrderRejection(int orderId, String errorMsg) {
        Order dbOrder = orderRepository.findByIbOrderId(orderId).orElse(null);
        if (dbOrder != null) {
            dbOrder.setStatus(Order.OrderStatus.REJECTED);
            orderRepository.save(dbOrder);
            log.warn("Order rejected by broker: ibOrderId={} msg={}", orderId, errorMsg);

            orderEventPublisher.publishSystemAlert("IBKR", "ORDER_REJECTED",
                    "Order rejected for ibOrderId=" + orderId + ": " + errorMsg);

            OrderGroup group = activeGroups.get(orderId);
            if (group != null) {
                deregisterOrderGroup(group);
                log.info("Bracket group deregistered after rejection: signal={} ibOrderId={}",
                        group.signalId(), orderId);
            }
        } else {
            log.warn("Order rejected by broker but not found in DB: ibOrderId={} msg={}", orderId, errorMsg);
            orderEventPublisher.publishSystemAlert("IBKR", "ORDER_REJECTED",
                    "Order rejected for untracked ibOrderId=" + orderId + ": " + errorMsg);
        }
    }
}
