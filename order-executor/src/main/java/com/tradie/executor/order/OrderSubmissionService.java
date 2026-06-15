package com.tradie.executor.order;

import com.ib.client.Contract;
import com.tradie.common.entity.Order;
import com.tradie.common.repository.OrderRepository;
import com.tradie.executor.dto.OrderDTO;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.OrderIdManager;
import com.tradie.executor.metrics.TradingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates bracket order submission to IBKR.
 *
 * <p>Flow:
 * <ol>
 *   <li>Reserve 3 consecutive IBKR order IDs</li>
 *   <li>Build the IBKR {@link Contract} for the symbol</li>
 *   <li>Build the three bracket {@link com.ib.client.Order} objects</li>
 *   <li>Persist all three as {@link Order} entities in the database</li>
 *   <li>Register the group with {@link OrderStatusTracker}</li>
 *   <li>Submit to IBKR via {@link IBConnectionManager#placeOrder}</li>
 *   <li>Publish a SUBMITTED event to the alerts topic</li>
 * </ol>
 */
@Service
public class OrderSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(OrderSubmissionService.class);

    private final IBConnectionManager connectionManager;
    private final ContractBuilder contractBuilder;
    private final BracketOrderBuilder bracketOrderBuilder;
    private final OrderIdManager orderIdManager;
    private final OrderRepository orderRepository;
    private final OrderStatusTracker orderStatusTracker;
    private final OrderEventPublisher orderEventPublisher;
    private final TradingMetrics tradingMetrics;

    public OrderSubmissionService(
            IBConnectionManager connectionManager,
            ContractBuilder contractBuilder,
            BracketOrderBuilder bracketOrderBuilder,
            OrderIdManager orderIdManager,
            OrderRepository orderRepository,
            OrderStatusTracker orderStatusTracker,
            OrderEventPublisher orderEventPublisher,
            TradingMetrics tradingMetrics) {
        this.connectionManager = connectionManager;
        this.contractBuilder = contractBuilder;
        this.bracketOrderBuilder = bracketOrderBuilder;
        this.orderIdManager = orderIdManager;
        this.orderRepository = orderRepository;
        this.orderStatusTracker = orderStatusTracker;
        this.orderEventPublisher = orderEventPublisher;
        this.tradingMetrics = tradingMetrics;
    }

    /**
     * Submits a bracket order (entry + take-profit + stop-loss) to IBKR.
     *
     * @param orderDTO the validated order from the Kafka consumer
     * @throws IllegalStateException if not connected to IBKR
     */
    public void submitBracketOrder(OrderDTO orderDTO) {
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Cannot submit order: not connected to IBKR");
        }

        // 1. Reserve 3 consecutive IBKR order IDs atomically
        int parentId = orderIdManager.reserveOrderIds(3);
        int tpId     = parentId + 1;
        int slId     = parentId + 2;

        log.info("Submitting bracket order: signal={} symbol={} side={} qty={} parentId={}",
                orderDTO.signalId(), orderDTO.symbol(), orderDTO.side(), orderDTO.quantity(), parentId);

        // 2. Build IBKR contract and bracket orders
        Contract contract = contractBuilder.build(orderDTO);
        List<com.ib.client.Order> bracketOrders = bracketOrderBuilder.build(orderDTO, parentId);

        // 3. Persist to database (parent, TP child, SL child)
        Order parentDb = persistOrder(orderDTO, parentId, true, null, Order.OrderType.LIMIT, orderDTO.limitPrice(), null);
        Order tpDb     = persistOrder(orderDTO, tpId, false, parentDb.getId(), Order.OrderType.LIMIT, orderDTO.takeProfit(), null);
        Order slDb     = persistOrder(orderDTO, slId, false, parentDb.getId(), Order.OrderType.STOP, null, orderDTO.stopLoss());

        // 4. Register in OrderStatusTracker
        OrderGroup group = new OrderGroup(
                orderDTO.signalId(),
                orderDTO.symbol(),
                orderDTO.exchange(),
                orderDTO.assetClass(),
                orderDTO.side().name(),
                orderDTO.quantity(),
                orderDTO.limitPrice(),
                orderDTO.stopLoss(),
                orderDTO.takeProfit(),
                orderDTO.strategy(),
                parentId, tpId, slId,
                parentDb.getId(), tpDb.getId(), slDb.getId(),
                Instant.now()
        );
        orderStatusTracker.registerOrderGroup(group);

        // 5. Submit to IBKR (parent first, then TP, then SL which triggers transmission).
        // Track placed legs so we can cancel them if a later leg fails, preventing an
        // unprotected live position (e.g. parent placed but SL never submitted).
        List<Integer> placedIds = new ArrayList<>();
        try {
            connectionManager.placeOrder(parentId, contract, bracketOrders.get(0));
            placedIds.add(parentId);
            connectionManager.placeOrder(tpId, contract, bracketOrders.get(1));
            placedIds.add(tpId);
            connectionManager.placeOrder(slId, contract, bracketOrders.get(2));
        } catch (Exception e) {
            log.error("Bracket placement failed after {} leg(s), compensating: {}", placedIds.size(), e.getMessage());
            compensate(placedIds, parentDb, tpDb, slDb, group);
            throw new IllegalStateException("Bracket order placement failed: " + e.getMessage(), e);
        }

        log.info("Bracket order submitted to IBKR: parentId={} tpId={} slId={}", parentId, tpId, slId);
        tradingMetrics.orderSubmitted(orderDTO.side());

        // 6. Publish submitted event
        orderEventPublisher.publishOrderEvent(new OrderEvent(
                "SUBMITTED",
                orderDTO.signalId(),
                orderDTO.symbol(),
                orderDTO.side().name(),
                orderDTO.quantity().doubleValue(),
                orderDTO.limitPrice().doubleValue(),
                orderDTO.strategy(),
                "Bracket order submitted: entry=" + orderDTO.limitPrice()
                        + " tp=" + orderDTO.takeProfit()
                        + " sl=" + orderDTO.stopLoss(),
                Instant.now(),
                null, null, null
        ));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void compensate(List<Integer> placedIds, Order parentDb, Order tpDb, Order slDb, OrderGroup group) {
        for (int id : placedIds) {
            try {
                connectionManager.cancelOrder(id);
            } catch (Exception ex) {
                log.error("Compensation cancel failed for ibOrderId={}: {}", id, ex.getMessage());
            }
        }
        cancelDbOrders(parentDb, tpDb, slDb);
        orderStatusTracker.deregisterOrderGroup(group);
    }

    private void cancelDbOrders(Order... orders) {
        for (Order o : orders) {
            o.setStatus(Order.OrderStatus.CANCELLED);
            o.setCancelledAt(Instant.now());
            orderRepository.save(o);
        }
    }

    private Order persistOrder(OrderDTO dto, int ibOrderId, boolean isBracketParent,
                               UUID parentDbId, Order.OrderType orderType,
                               BigDecimal limitPrice, BigDecimal stopPrice) {
        Order.OrderSide exitSide = dto.side() == Order.OrderSide.BUY
                ? Order.OrderSide.SELL : Order.OrderSide.BUY;

        Order order = new Order();
        order.setSignalId(dto.signalId());
        order.setIbOrderId(ibOrderId);
        order.setSymbol(dto.symbol());
        order.setExchange(dto.exchange());
        order.setAssetClass(dto.assetClass());
        order.setSide(isBracketParent ? dto.side() : exitSide);
        order.setOrderType(orderType);
        order.setQuantity(dto.quantity());
        order.setLimitPrice(limitPrice);
        order.setStopPrice(stopPrice);
        order.setIsBracketParent(isBracketParent);
        order.setParentOrderId(parentDbId);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setSubmittedAt(Instant.now());
        return orderRepository.save(order);
    }
}
