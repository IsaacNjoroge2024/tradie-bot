package com.tradie.executor.position;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.config.PositionProperties;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.order.OrderEventPublisher;
import com.tradie.executor.order.OrderStatusTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages trailing stop adjustments for open positions.
 * Tracks high water marks in memory and modifies IBKR stop orders when the
 * trail threshold is crossed.
 */
@Service
public class TrailingStopManager {

    private static final Logger log = LoggerFactory.getLogger(TrailingStopManager.class);

    private final PositionProperties positionProperties;
    private final PositionRepository positionRepository;
    private final OrderStatusTracker orderStatusTracker;
    private final OrderEventPublisher orderEventPublisher;

    // positionId → high water mark price
    private final ConcurrentHashMap<UUID, BigDecimal> highWaterMarks = new ConcurrentHashMap<>();

    // Lazy to break circular dependency: IBConnectionManager → MarketDataService → TrailingStopManager → IBConnectionManager
    private volatile IBConnectionManager connectionManager;

    public TrailingStopManager(
            PositionProperties positionProperties,
            PositionRepository positionRepository,
            OrderStatusTracker orderStatusTracker,
            OrderEventPublisher orderEventPublisher) {
        this.positionProperties = positionProperties;
        this.positionRepository = positionRepository;
        this.orderStatusTracker = orderStatusTracker;
        this.orderEventPublisher = orderEventPublisher;
    }

    public void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Checks whether the trailing stop should be adjusted for the given position
     * and modifies the IBKR stop order if the threshold is crossed.
     */
    public void checkAndAdjust(Position position, BigDecimal currentPrice) {
        PositionProperties.TrailingStop config = positionProperties.getTrailingStop();
        if (!config.isEnabled()) return;
        if (position.getStatus() != Position.PositionStatus.OPEN) return;

        double trailPct = position.getTrailingStopPct() != null
                ? position.getTrailingStopPct()
                : config.getDefaultTrailPct();

        double pnlPct = computePnlPct(position, currentPrice);
        if (pnlPct < config.getActivationPct()) return;

        boolean isLong = position.getSide() == Order.OrderSide.BUY;
        BigDecimal hwm = highWaterMarks.getOrDefault(position.getId(), position.getEntryPrice());

        if (isLong) {
            if (currentPrice.compareTo(hwm) <= 0) return;
            highWaterMarks.put(position.getId(), currentPrice);
            BigDecimal newStop = currentPrice.multiply(BigDecimal.valueOf(1.0 - trailPct / 100))
                    .setScale(8, RoundingMode.HALF_UP);
            BigDecimal currentStop = position.getStopLoss() != null ? position.getStopLoss() : BigDecimal.ZERO;
            BigDecimal stepSize = position.getEntryPrice()
                    .multiply(BigDecimal.valueOf(config.getStepPct() / 100));
            if (newStop.subtract(currentStop).compareTo(stepSize) > 0) {
                adjustStop(position, newStop);
            }
        } else {
            // SHORT: stop trails price downward
            if (currentPrice.compareTo(hwm) >= 0) return;
            highWaterMarks.put(position.getId(), currentPrice);
            BigDecimal newStop = currentPrice.multiply(BigDecimal.valueOf(1.0 + trailPct / 100))
                    .setScale(8, RoundingMode.HALF_UP);
            BigDecimal currentStop = position.getStopLoss() != null
                    ? position.getStopLoss() : BigDecimal.valueOf(Double.MAX_VALUE);
            BigDecimal stepSize = position.getEntryPrice()
                    .multiply(BigDecimal.valueOf(config.getStepPct() / 100));
            if (currentStop.subtract(newStop).compareTo(stepSize) > 0) {
                adjustStop(position, newStop);
            }
        }
    }

    private void adjustStop(Position position, BigDecimal newStopPrice) {
        position.setStopLoss(newStopPrice);
        positionRepository.save(position);

        orderStatusTracker.findGroupBySignalId(position.getEntrySignalId()).ifPresent(group -> {
            if (connectionManager != null && connectionManager.isConnected()) {
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
                log.info("Trailing stop adjusted: symbol={} newStop={} positionId={}",
                        position.getSymbol(), newStopPrice, position.getId());
            }
        });

        orderEventPublisher.publishOrderEvent(new OrderEvent(
                "TRAILING_STOP_MOVED",
                position.getEntrySignalId(),
                position.getSymbol(),
                position.getSide().name(),
                position.getQuantity().doubleValue(),
                newStopPrice.doubleValue(),
                position.getStrategy(),
                "Trailing stop moved to " + newStopPrice,
                Instant.now()
        ));
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

    private double computePnlPct(Position position, BigDecimal currentPrice) {
        BigDecimal cost = position.getEntryPrice().multiply(position.getQuantity());
        if (cost.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        BigDecimal diff = currentPrice.subtract(position.getEntryPrice());
        if (position.getSide() == Order.OrderSide.SELL) diff = diff.negate();
        return diff.multiply(position.getQuantity())
                .divide(cost, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public void clearHighWaterMark(UUID positionId) {
        highWaterMarks.remove(positionId);
    }
}
