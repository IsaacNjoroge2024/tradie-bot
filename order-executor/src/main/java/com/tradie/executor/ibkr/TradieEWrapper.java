package com.tradie.executor.ibkr;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.DefaultEWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.TickAttrib;
import com.tradie.executor.dto.IBPosition;
import com.tradie.executor.order.OrderStatusTracker;
import com.tradie.executor.position.MarketDataService;
import com.tradie.executor.position.PositionSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IBKR EWrapper implementation for Tradie Bot.
 *
 * Extends DefaultEWrapper so only the callbacks relevant to this service
 * need to be overridden. All other 100+ EWrapper methods are no-ops by default.
 *
 * Responsibilities:
 * - Route account/position/order callbacks to the relevant services
 * - Update the last-message timestamp (used by heartbeat monitoring)
 * - Notify IBConnectionManager of connection events via IBConnectionCallback
 */
public class TradieEWrapper extends DefaultEWrapper {

    private static final Logger log = LoggerFactory.getLogger(TradieEWrapper.class);

    /**
     * Callback interface implemented by IBConnectionManager to receive
     * connection lifecycle events without creating a circular Spring dependency.
     */
    public interface IBConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onError(int errorCode, String errorMsg);
    }

    private final IbkrAccountService accountService;
    private final PositionTracker positionTracker;
    private final OrderIdManager orderIdManager;
    private final OrderStatusTracker orderStatusTracker;
    private final IBConnectionCallback connectionCallback;
    private final PositionSynchronizer positionSynchronizer;
    private final MarketDataService marketDataService;
    private final AtomicReference<Instant> lastMessageTime = new AtomicReference<>(Instant.now());

    public TradieEWrapper(
            IbkrAccountService accountService,
            PositionTracker positionTracker,
            OrderIdManager orderIdManager,
            OrderStatusTracker orderStatusTracker,
            IBConnectionCallback connectionCallback,
            PositionSynchronizer positionSynchronizer,
            MarketDataService marketDataService) {
        this.accountService = accountService;
        this.positionTracker = positionTracker;
        this.orderIdManager = orderIdManager;
        this.orderStatusTracker = orderStatusTracker;
        this.connectionCallback = connectionCallback;
        this.positionSynchronizer = positionSynchronizer;
        this.marketDataService = marketDataService;
    }

    // ─── Heartbeat ────────────────────────────────────────────────────────────

    /** Called on every EWrapper callback to update the last-seen timestamp. */
    private void touchLastMessageTime() {
        lastMessageTime.set(Instant.now());
    }

    public Instant getLastMessageTime() {
        return lastMessageTime.get();
    }

    // ─── Connection Callbacks ─────────────────────────────────────────────────

    @Override
    public void connectAck() {
        touchLastMessageTime();
        log.info("IBKR connection acknowledged");
    }

    @Override
    public void connectionClosed() {
        touchLastMessageTime();
        log.warn("IBKR connection closed");
        connectionCallback.onDisconnected();
    }

    // ─── Order ID ─────────────────────────────────────────────────────────────

    @Override
    public void nextValidId(int orderId) {
        touchLastMessageTime();
        orderIdManager.onNextValidId(orderId);
        log.info("Next valid order ID received: {}", orderId);
        connectionCallback.onConnected();
    }

    // ─── Account ──────────────────────────────────────────────────────────────

    @Override
    public void managedAccounts(String accountsList) {
        touchLastMessageTime();
        accountService.onManagedAccounts(accountsList);
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        touchLastMessageTime();
        accountService.onAccountSummary(tag, value);
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        touchLastMessageTime();
        accountService.onAccountSummaryEnd();
    }

    // ─── Positions ────────────────────────────────────────────────────────────

    @Override
    public void position(String account, Contract contract, Decimal pos, double avgCost) {
        touchLastMessageTime();
        IBPosition ibPosition = new IBPosition(
                contract.symbol(),
                contract.exchange() != null ? contract.exchange() : "",
                pos.value().doubleValue(),
                avgCost,
                0.0,
                0.0
        );
        positionTracker.updatePosition(ibPosition);
    }

    @Override
    public void positionEnd() {
        touchLastMessageTime();
        log.debug("Position snapshot complete. Open positions: {}", positionTracker.getOpenPositionCount());
        positionSynchronizer.onPositionsLoaded();
    }

    // ─── Orders ───────────────────────────────────────────────────────────────

    @Override
    public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice,
                            int clientId, String whyHeld, double mktCapPrice) {
        touchLastMessageTime();
        log.info("Order status: orderId={} status={} filled={} remaining={} avgFillPrice={}",
                orderId, status, filled, remaining, avgFillPrice);
        orderStatusTracker.handleStatusUpdate(orderId, status, filled, remaining, avgFillPrice);
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        touchLastMessageTime();
        log.debug("Open order: orderId={} symbol={} action={} status={}",
                orderId, contract.symbol(), order.action(), orderState.status());
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        touchLastMessageTime();
        log.info("Execution details: orderId={} symbol={} side={} shares={} price={}",
                execution.orderId(), contract.symbol(), execution.side(),
                execution.shares(), execution.price());
    }

    // ─── Market Data ──────────────────────────────────────────────────────────

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        touchLastMessageTime();
        marketDataService.onTickPrice(tickerId, field, price);
    }

    // ─── Error Handling ───────────────────────────────────────────────────────

    @Override
    public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        touchLastMessageTime();
        handleError(id, errorCode, errorMsg);
    }

    @Override
    public void error(Exception e) {
        touchLastMessageTime();
        log.error("IBKR API exception: {}", e.getMessage(), e);
        connectionCallback.onError(-1, e.getMessage());
    }

    @Override
    public void error(String str) {
        touchLastMessageTime();
        log.warn("IBKR warning: {}", str);
    }

    private void handleError(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 502 -> log.error("Cannot connect to TWS/Gateway (502): {}. Is TWS running?", errorMsg);
            case 504 -> {
                log.error("Not connected (504): {}. Triggering reconnect.", errorMsg);
                connectionCallback.onError(errorCode, errorMsg);
            }
            case 1100 -> {
                log.warn("Connectivity lost (1100): {}. Auto-reconnecting.", errorMsg);
                connectionCallback.onError(errorCode, errorMsg);
            }
            case 1101 -> {
                log.info("Connectivity restored (1101): {}. Resuming operations.", errorMsg);
                connectionCallback.onConnected();
            }
            case 2110 -> {
                log.warn("Connectivity restored but data lost (2110): {}. Re-requesting positions.", errorMsg);
                connectionCallback.onConnected();
            }
            case 201 -> log.error("Order rejected (201): id={} msg={}", id, errorMsg);
            case 202 -> log.warn("Order cancelled (202): id={} msg={}", id, errorMsg);
            default  -> {
                if (errorCode < 1000) {
                    log.error("IBKR error: code={} id={} msg={}", errorCode, id, errorMsg);
                } else {
                    log.warn("IBKR warning: code={} id={} msg={}", errorCode, id, errorMsg);
                }
            }
        }
    }
}
