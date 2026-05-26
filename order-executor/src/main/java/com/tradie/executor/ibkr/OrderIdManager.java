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
     * Returns the current next order ID without incrementing (for status display).
     */
    public int peekNextOrderId() {
        return nextOrderId.get();
    }
}
