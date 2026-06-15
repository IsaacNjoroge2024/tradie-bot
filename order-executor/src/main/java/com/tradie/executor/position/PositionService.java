package com.tradie.executor.position;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.OrderIdManager;
import com.tradie.executor.order.OrderEventPublisher;
import com.tradie.executor.order.OrderStatusTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradie.executor.order.OrderGroup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Position lifecycle service for API-driven operations (manual close, stop/target modification).
 * Automated position lifecycle (open/close from bracket fills) is handled by OrderStatusTracker.
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positionRepository;
    private final IBConnectionManager connectionManager;
    private final OrderStatusTracker orderStatusTracker;
    private final OrderIdManager orderIdManager;
    private final MarketDataService marketDataService;
    private final PnLCalculator pnlCalculator;
    private final OrderEventPublisher orderEventPublisher;

    public PositionService(
            PositionRepository positionRepository,
            IBConnectionManager connectionManager,
            OrderStatusTracker orderStatusTracker,
            OrderIdManager orderIdManager,
            MarketDataService marketDataService,
            PnLCalculator pnlCalculator,
            OrderEventPublisher orderEventPublisher) {
        this.positionRepository = positionRepository;
        this.connectionManager = connectionManager;
        this.orderStatusTracker = orderStatusTracker;
        this.orderIdManager = orderIdManager;
        this.marketDataService = marketDataService;
        this.pnlCalculator = pnlCalculator;
        this.orderEventPublisher = orderEventPublisher;
    }

    public List<Position> getOpenPositions() {
        return positionRepository.findByStatus(Position.PositionStatus.OPEN);
    }

    public Optional<Position> getPosition(UUID id) {
        return positionRepository.findById(id);
    }

    /**
     * Manually closes a position by cancelling its bracket children and
     * submitting a MARKET order to flatten the position in IBKR.
     */
    public void closePosition(UUID positionId, String reason) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + positionId));

        if (position.getStatus() == Position.PositionStatus.CLOSING) {
            throw new IllegalStateException("Position close already in progress: " + positionId);
        }
        if (position.getStatus() != Position.PositionStatus.OPEN) {
            throw new IllegalStateException("Position is not open: " + positionId);
        }
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Not connected to IBKR");
        }

        // Durably mark CLOSING before any irreversible side effects so a retry cannot
        // submit a second market order if the process crashes between cancel and placeOrder.
        position.setStatus(Position.PositionStatus.CLOSING);
        positionRepository.save(position);

        boolean bracketsDeregistered = false;
        try {
            // Cancel existing TP and SL bracket children
            Optional<OrderGroup> group = orderStatusTracker.findGroupBySignalId(position.getEntrySignalId());
            if (group.isPresent()) {
                connectionManager.cancelOrder(group.get().takeProfitIbOrderId());
                connectionManager.cancelOrder(group.get().stopLossIbOrderId());
                orderStatusTracker.deregisterOrderGroup(group.get());
                bracketsDeregistered = true;
                log.info("Bracket orders cancelled for manual close: signal={}", group.get().signalId());
            }

            // Submit MARKET order to close
            int closeOrderId = orderIdManager.getNextOrderId();
            String exitSide = position.getSide() == Order.OrderSide.BUY ? "SELL" : "BUY";
            com.ib.client.Order marketOrder = new com.ib.client.Order();
            marketOrder.orderId(closeOrderId);
            marketOrder.action(exitSide);
            marketOrder.orderType("MKT");
            marketOrder.totalQuantity(Decimal.get(position.getQuantity().doubleValue()));
            marketOrder.transmit(true);

            Contract contract = buildContract(position);
            connectionManager.placeOrder(closeOrderId, contract, marketOrder);
            log.info("Manual close MARKET order submitted: positionId={} symbol={} ibOrderId={}",
                    positionId, position.getSymbol(), closeOrderId);

        } catch (Exception e) {
            if (bracketsDeregistered) {
                // TP/SL already cancelled at IBKR — reverting to OPEN would leave the position
                // with no stop-loss protection. Keep CLOSING; manual intervention required.
                log.error("CRITICAL: placeOrder failed after brackets were cancelled for positionId={}."
                        + " Position stays CLOSING — manual intervention required.", positionId, e);
            } else {
                position.setStatus(Position.PositionStatus.OPEN);
                positionRepository.save(position);
            }
            throw new IllegalStateException("Failed to submit close order: " + e.getMessage(), e);
        }

        // Store indicative exit price from market quote — confirmed by PositionSynchronizer on fill
        BigDecimal currentPrice = marketDataService.getLatestPrice(position.getSymbol());
        if (currentPrice != null) {
            position.setExitPrice(currentPrice);
            position.setRealizedPnl(pnlCalculator.realizedPnl(position, currentPrice));
            positionRepository.save(position);
        }

        orderEventPublisher.publishOrderEvent(new OrderEvent(
                "POSITION_CLOSED_MANUAL",
                position.getEntrySignalId(),
                position.getSymbol(),
                position.getSide().name(),
                position.getQuantity().doubleValue(),
                currentPrice != null ? currentPrice.doubleValue() : 0.0,
                position.getStrategy(),
                "Manual close: " + reason,
                Instant.now(),
                null,
                null,
                position.getEntryPrice().doubleValue()
        ));
    }

    /**
     * Modifies the stop loss price for an open position.
     */
    public void modifyStop(UUID positionId, BigDecimal newStopPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + positionId));

        if (position.getStatus() != Position.PositionStatus.OPEN) {
            throw new IllegalStateException("Position is not open: " + positionId);
        }
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Not connected to IBKR");
        }

        Optional<OrderGroup> stopGroup = orderStatusTracker.findGroupBySignalId(position.getEntrySignalId());
        if (stopGroup.isEmpty()) {
            log.warn("No active bracket group for position {}; IBKR stop not modified", positionId);
        }
        stopGroup.ifPresent(group -> {
            com.ib.client.Order stopOrder = new com.ib.client.Order();
            stopOrder.orderId(group.stopLossIbOrderId());
            stopOrder.parentId(group.parentIbOrderId());
            String exitSide = position.getSide() == Order.OrderSide.BUY ? "SELL" : "BUY";
            stopOrder.action(exitSide);
            stopOrder.orderType("STP");
            stopOrder.totalQuantity(Decimal.get(position.getQuantity().doubleValue()));
            stopOrder.auxPrice(newStopPrice.doubleValue());
            stopOrder.transmit(true);
            connectionManager.placeOrder(group.stopLossIbOrderId(), buildContract(position), stopOrder);
        });

        position.setStopLoss(newStopPrice);
        positionRepository.save(position);
        log.info("Stop loss modified: positionId={} symbol={} newStop={}",
                positionId, position.getSymbol(), newStopPrice);
    }

    /**
     * Modifies the take profit price for an open position.
     */
    public void modifyTarget(UUID positionId, BigDecimal newTargetPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Position not found: " + positionId));

        if (position.getStatus() != Position.PositionStatus.OPEN) {
            throw new IllegalStateException("Position is not open: " + positionId);
        }
        if (!connectionManager.isConnected()) {
            throw new IllegalStateException("Not connected to IBKR");
        }

        Optional<OrderGroup> tpGroup = orderStatusTracker.findGroupBySignalId(position.getEntrySignalId());
        if (tpGroup.isEmpty()) {
            log.warn("No active bracket group for position {}; IBKR target not modified", positionId);
        }
        tpGroup.ifPresent(group -> {
            com.ib.client.Order tpOrder = new com.ib.client.Order();
            tpOrder.orderId(group.takeProfitIbOrderId());
            tpOrder.parentId(group.parentIbOrderId());
            String exitSide = position.getSide() == Order.OrderSide.BUY ? "SELL" : "BUY";
            tpOrder.action(exitSide);
            tpOrder.orderType("LMT");
            tpOrder.totalQuantity(Decimal.get(position.getQuantity().doubleValue()));
            tpOrder.lmtPrice(newTargetPrice.doubleValue());
            tpOrder.transmit(true);
            connectionManager.placeOrder(group.takeProfitIbOrderId(), buildContract(position), tpOrder);
        });

        position.setTakeProfit(newTargetPrice);
        positionRepository.save(position);
        log.info("Take profit modified: positionId={} symbol={} newTarget={}",
                positionId, position.getSymbol(), newTargetPrice);
    }

    private Contract buildContract(Position position) {
        Contract contract = new Contract();
        contract.symbol(position.getSymbol());
        contract.currency("USD");
        switch (position.getAssetClass().toUpperCase()) {
            case "STK"           -> { contract.secType("STK");    contract.exchange("SMART");    }
            case "CASH", "FOREX" -> { contract.secType("CASH");   contract.exchange("IDEALPRO"); }
            case "FUT"           -> { contract.secType("FUT");    contract.exchange(position.getExchange()); }
            case "CRYPTO"        -> { contract.secType("CRYPTO"); contract.exchange("PAXOS");    }
            default              -> { contract.secType("STK");    contract.exchange("SMART");    }
        }
        return contract;
    }
}
