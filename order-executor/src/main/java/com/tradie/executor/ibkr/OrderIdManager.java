package com.tradie.executor.ibkr;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages IBKR order IDs.
 *
 * IBKR requires unique, strictly incrementing order IDs per client session.
 * The initial ID is provided by the nextValidId callback on every connection.
 * No persistence is needed: IBKR tracks the authoritative ID and resets us
 * to the correct starting point on each reconnection.
 */
@Component
public class OrderIdManager {

    private final AtomicInteger nextOrderId = new AtomicInteger(0);

    /**
     * Called by TradieEWrapper when IBKR sends the nextValidId callback.
     * Resets the counter to the IBKR-provided starting ID.
     */
    public void onNextValidId(int orderId) {
        nextOrderId.set(orderId);
    }

    /**
     * Returns the next available order ID and atomically increments the counter.
     */
    public int getNextOrderId() {
        return nextOrderId.getAndIncrement();
    }

    /**
     * Atomically reserves {@code count} consecutive order IDs and returns the first one.
     * Used by bracket order submission to claim parentId, parentId+1, and parentId+2 in one step.
     *
     * @param count number of consecutive IDs to reserve (e.g., 3 for a bracket order)
     * @return the first reserved ID; subsequent IDs are {@code result + 1}, {@code result + 2}, etc.
     */
    public int reserveOrderIds(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0, got: " + count);
        }
        return nextOrderId.getAndAdd(count);
    }

    /**
     * Returns the current next order ID without incrementing (for status display).
     */
    public int peekNextOrderId() {
        return nextOrderId.get();
    }
}
