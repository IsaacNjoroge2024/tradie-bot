package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.dto.IBPosition;
import com.tradie.executor.dto.OrderEvent;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.PositionTracker;
import com.tradie.executor.order.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reconciles DB positions with live IBKR position data on startup and periodically.
 *
 * <p>Reconciliation rules:
 * <ul>
 *   <li>IBKR has position, DB doesn't → create a DB record (manual/external trade)</li>
 *   <li>DB has OPEN position, IBKR doesn't → mark as CLOSED (filled while disconnected)</li>
 *   <li>Quantities differ → update DB to match IBKR (IBKR is source of truth)</li>
 * </ul>
 */
@Service
public class PositionSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PositionSynchronizer.class);

    private final PositionRepository positionRepository;
    private final PositionTracker positionTracker;
    private final OrderEventPublisher orderEventPublisher;
    private final MarketDataService marketDataService;

    // Lazy to break circular dependency with IBConnectionManager
    private volatile IBConnectionManager connectionManager;

    public PositionSynchronizer(
            PositionRepository positionRepository,
            PositionTracker positionTracker,
            OrderEventPublisher orderEventPublisher,
            MarketDataService marketDataService) {
        this.positionRepository = positionRepository;
        this.positionTracker = positionTracker;
        this.orderEventPublisher = orderEventPublisher;
        this.marketDataService = marketDataService;
    }

    public void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Called by TradieEWrapper when positionEnd() fires (IBKR position snapshot complete).
     */
    public void onPositionsLoaded() {
        log.info("IBKR position snapshot received. Starting reconciliation.");
        syncPositions();
        marketDataService.subscribeToOpenPositions();
    }

    /**
     * Periodic sync every configurable interval (default 300 s).
     * Re-requests positions from IBKR; reconciliation triggers on positionEnd callback.
     */
    @Scheduled(fixedRateString = "${tradie.positions.sync-interval-seconds:300}000",
               initialDelayString = "${tradie.positions.sync-interval-seconds:300}000")
    public void scheduledSync() {
        if (connectionManager != null && connectionManager.isConnected()) {
            log.debug("Requesting periodic IBKR position sync");
            connectionManager.requestPositions();
        }
    }

    // ─── Reconciliation ───────────────────────────────────────────────────────

    void syncPositions() {
        Map<String, IBPosition> ibkrPositions = positionTracker.getPositions();
        List<Position> dbOpenPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);

        // Case 1: DB has OPEN position but IBKR doesn't → mark CLOSED
        for (Position dbPos : dbOpenPositions) {
            String key = dbPos.getSymbol() + "|" + dbPos.getExchange();
            if (!ibkrPositions.containsKey(key)) {
                log.warn("Position closed in IBKR but OPEN in DB (filled while disconnected): symbol={} id={}",
                        dbPos.getSymbol(), dbPos.getId());
                dbPos.setStatus(Position.PositionStatus.CLOSED);
                dbPos.setClosedAt(Instant.now());
                positionRepository.save(dbPos);
                publishSyncEvent(dbPos, "Position closed in IBKR; DB updated to CLOSED");
            }
        }

        // Case 2: IBKR has position, check DB for quantity mismatch or missing record
        for (Map.Entry<String, IBPosition> entry : ibkrPositions.entrySet()) {
            IBPosition ibPos = entry.getValue();
            List<Position> matching = positionRepository.findByStatusAndSymbol(
                    Position.PositionStatus.OPEN, ibPos.symbol());

            if (matching.isEmpty()) {
                // IBKR has position, DB doesn't — create a record (manual trade)
                log.warn("Position found in IBKR but not in DB (manual trade?): symbol={}", ibPos.symbol());
                Position newPos = buildFromIbkr(ibPos);
                positionRepository.save(newPos);
                marketDataService.subscribeToPosition(newPos);
                publishSyncEvent(newPos, "Position created from IBKR sync (manual trade)");
            } else {
                // Check for quantity mismatch
                for (Position dbPos : matching) {
                    BigDecimal ibQty = BigDecimal.valueOf(Math.abs(ibPos.quantity()));
                    if (dbPos.getQuantity().compareTo(ibQty) != 0) {
                        log.warn("Quantity mismatch for {}: DB={} IBKR={}. Updating to IBKR value.",
                                ibPos.symbol(), dbPos.getQuantity(), ibQty);
                        dbPos.setQuantity(ibQty);
                        positionRepository.save(dbPos);
                    }
                }
            }
        }

        log.info("Position reconciliation complete. IBKR positions={} DB open={}",
                ibkrPositions.size(), positionRepository.countByStatus(Position.PositionStatus.OPEN));
    }

    private Position buildFromIbkr(IBPosition ibPos) {
        Position position = new Position();
        position.setSymbol(ibPos.symbol());
        position.setExchange(ibPos.exchange() != null && !ibPos.exchange().isBlank() ? ibPos.exchange() : "UNKNOWN");
        position.setAssetClass("STK");
        position.setSide(ibPos.quantity() >= 0 ? Order.OrderSide.BUY : Order.OrderSide.SELL);
        position.setQuantity(BigDecimal.valueOf(Math.abs(ibPos.quantity())));
        position.setEntryPrice(BigDecimal.valueOf(ibPos.avgCost()));
        position.setStatus(Position.PositionStatus.OPEN);
        return position;
    }

    private void publishSyncEvent(Position position, String message) {
        orderEventPublisher.publishOrderEvent(new OrderEvent(
                "POSITION_SYNC",
                position.getEntrySignalId(),
                position.getSymbol(),
                position.getSide() != null ? position.getSide().name() : "UNKNOWN",
                position.getQuantity().doubleValue(),
                position.getEntryPrice() != null ? position.getEntryPrice().doubleValue() : 0.0,
                position.getStrategy(),
                message,
                Instant.now()
        ));
    }
}
