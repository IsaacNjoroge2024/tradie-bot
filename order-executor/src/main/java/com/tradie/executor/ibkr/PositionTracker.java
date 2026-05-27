package com.tradie.executor.ibkr;

import com.tradie.executor.dto.IBPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks real-time IBKR positions received via position() callbacks.
 * Positions are keyed by "symbol|exchange" to prevent collisions across
 * instruments that share the same symbol on different exchanges.
 */
@Component
public class PositionTracker {

    private static final Logger log = LoggerFactory.getLogger(PositionTracker.class);

    private final Map<String, IBPosition> positions = new ConcurrentHashMap<>();

    private String keyFor(IBPosition position) {
        return position.symbol() + "|" + position.exchange();
    }

    /**
     * Called by TradieEWrapper when a position update is received from IBKR.
     * A position with quantity == 0 indicates the position has been closed.
     */
    public void updatePosition(IBPosition position) {
        if (position.quantity() == 0) {
            positions.remove(keyFor(position));
            log.debug("Position closed: symbol={}", position.symbol());
        } else {
            positions.put(keyFor(position), position);
            log.debug("Position updated: symbol={} qty={} avgCost={}",
                    position.symbol(), position.quantity(), position.avgCost());
        }
    }

    /**
     * Returns an immutable snapshot of all current positions.
     */
    public Map<String, IBPosition> getPositions() {
        return Map.copyOf(positions);
    }

    /**
     * Returns the number of open positions.
     */
    public int getOpenPositionCount() {
        return positions.size();
    }

    /**
     * Clears all tracked positions (called on disconnect/reconnect to avoid stale data).
     */
    public void clear() {
        positions.clear();
        log.info("Position tracker cleared");
    }
}
