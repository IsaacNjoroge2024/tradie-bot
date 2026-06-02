package com.tradie.executor.ibkr;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.Order;
import com.ib.client.OrderCancel;
import com.tradie.executor.config.IbkrProperties;
import com.tradie.executor.order.OrderStatusTracker;
import com.tradie.executor.position.MarketDataService;
import com.tradie.executor.position.PositionSynchronizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the IBKR TWS/Gateway socket connection lifecycle.
 *
 * Responsibilities:
 * - Initiates connection on startup via @PostConstruct
 * - Starts the EReader message-processing thread
 * - Implements IBConnectionCallback to handle connection events from TradieEWrapper
 * - Manages reconnection with exponential backoff (max 10 attempts)
 * - Cleanly disconnects on Spring context shutdown via @PreDestroy
 */
@Service
public class IBConnectionManager implements TradieEWrapper.IBConnectionCallback {

    private static final Logger log = LoggerFactory.getLogger(IBConnectionManager.class);
    private static final int[] BACKOFF_SECONDS = {1, 2, 4, 8, 16, 32, 60};

    private final IbkrProperties properties;
    private final IbkrAccountService accountService;
    private final PositionTracker positionTracker;
    private final OrderIdManager orderIdManager;
    private final OrderStatusTracker orderStatusTracker;
    private final PositionSynchronizer positionSynchronizer;
    private final MarketDataService marketDataService;

    private EClientSocket client;
    private EJavaSignal signal;
    private TradieEWrapper wrapper;
    private Thread messageProcessorThread;

    private final AtomicReference<ConnectionState> state =
            new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicReference<Instant> connectionTime = new AtomicReference<>();
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ibkr-reconnect");
                t.setDaemon(true);
                return t;
            });

    public IBConnectionManager(
            IbkrProperties properties,
            IbkrAccountService accountService,
            PositionTracker positionTracker,
            OrderIdManager orderIdManager,
            OrderStatusTracker orderStatusTracker,
            PositionSynchronizer positionSynchronizer,
            MarketDataService marketDataService) {
        this.properties = properties;
        this.accountService = accountService;
        this.positionTracker = positionTracker;
        this.orderIdManager = orderIdManager;
        this.orderStatusTracker = orderStatusTracker;
        this.positionSynchronizer = positionSynchronizer;
        this.marketDataService = marketDataService;
    }

    @PostConstruct
    public void init() {
        accountService.setConnectionManager(this);
        orderStatusTracker.setConnectionManager(this);
        positionSynchronizer.setConnectionManager(this);
        marketDataService.setConnectionManager(this);
        wrapper = new TradieEWrapper(accountService, positionTracker, orderIdManager,
                orderStatusTracker, this, positionSynchronizer, marketDataService);
        signal = new EJavaSignal();
        client = new EClientSocket(wrapper, signal);
        connect();
    }

    // ─── Connection Lifecycle ─────────────────────────────────────────────────

    public synchronized void connect() {
        if (state.get() == ConnectionState.CONNECTING || state.get() == ConnectionState.CONNECTED) {
            log.debug("Connection already in progress or established, skipping");
            return;
        }

        state.set(ConnectionState.CONNECTING);
        log.info("Connecting to IBKR TWS at {}:{} clientId={}",
                properties.getHost(), properties.getPort(), properties.getClientId());

        try {
            client.eConnect(properties.getHost(), properties.getPort(), properties.getClientId());

            if (!client.isConnected()) {
                log.error("Failed to connect to IBKR TWS — is TWS running on port {}?", properties.getPort());
                state.set(ConnectionState.ERROR);
                scheduleReconnect();
                return;
            }

            startMessageProcessorThread();
            // Connection is confirmed via nextValidId callback → onConnected()

        } catch (Exception e) {
            log.error("Exception during IBKR connect: {}", e.getMessage(), e);
            state.set(ConnectionState.ERROR);
            scheduleReconnect();
        }
    }

    public synchronized void disconnect() {
        if (client != null && client.isConnected()) {
            log.info("Disconnecting from IBKR");
            client.eDisconnect();
        }
        stopMessageProcessorThread();
        state.set(ConnectionState.DISCONNECTED);
        positionTracker.clear();
        marketDataService.cancelAllSubscriptions();
    }

    private void scheduleReconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) {
            log.debug("Reconnect already scheduled, skipping duplicate request");
            return;
        }

        int attempt = reconnectAttempt.get();
        if (attempt >= properties.getMaxReconnectAttempts()) {
            reconnectScheduled.set(false);
            log.error("Max reconnection attempts ({}) reached. Manual intervention required.",
                    properties.getMaxReconnectAttempts());
            state.set(ConnectionState.ERROR);
            return;
        }

        int delaySec = BACKOFF_SECONDS[Math.min(attempt, BACKOFF_SECONDS.length - 1)];
        reconnectAttempt.incrementAndGet();
        log.info("Scheduling reconnect attempt {} in {}s", attempt + 1, delaySec);
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            reconnect();
        }, delaySec, TimeUnit.SECONDS);
    }

    private void reconnect() {
        log.info("Attempting reconnect (attempt {})", reconnectAttempt.get());
        disconnect();
        connect();
    }

    // ─── IBConnectionCallback Implementation ─────────────────────────────────

    @Override
    public void onConnected() {
        state.set(ConnectionState.CONNECTED);
        connectionTime.set(Instant.now());
        reconnectAttempt.set(0);
        log.info("IBKR connection established. Requesting account summary and positions.");
        // Always clear stale positions before re-requesting to avoid serving outdated data
        positionTracker.clear();
        requestAccountSummary(IbkrAccountService.getAccountSummaryReqId(), IbkrAccountService.getAccountTags());
        requestPositions();
        // Market data subscriptions are re-established in PositionSynchronizer.onPositionsLoaded()
    }

    @Override
    public void onDisconnected() {
        if (state.get() != ConnectionState.DISCONNECTED) {
            state.set(ConnectionState.DISCONNECTED);
            positionTracker.clear();
            scheduleReconnect();
        }
    }

    @Override
    public void onError(int errorCode, String errorMsg) {
        switch (errorCode) {
            case 504, 1100 -> {
                // Not connected / connectivity lost — trigger reconnect
                state.set(ConnectionState.DISCONNECTED);
                positionTracker.clear();
                scheduleReconnect();
            }
            default -> log.warn("Unhandled IBKR error in connection manager: code={} msg={}", errorCode, errorMsg);
        }
    }

    // ─── IBKR Requests ───────────────────────────────────────────────────────

    public void requestAccountSummary(int reqId, String tags) {
        if (isConnected()) {
            client.reqAccountSummary(reqId, "All", tags);
            log.debug("Account summary requested: reqId={}", reqId);
        }
    }

    public void requestPositions() {
        if (isConnected()) {
            client.reqPositions();
            log.debug("Positions requested");
        }
    }

    /**
     * Submits an order to IBKR. Must only be called when connected.
     *
     * @param orderId  the IBKR order ID (from {@link OrderIdManager})
     * @param contract the IBKR contract representing the instrument
     * @param order    the IBKR order with action, type, price, and quantity
     */
    public void placeOrder(int orderId, Contract contract, Order order) {
        if (isConnected()) {
            client.placeOrder(orderId, contract, order);
            log.debug("Order placed: ibOrderId={}", orderId);
        } else {
            throw new IllegalStateException("Cannot place order: not connected to IBKR");
        }
    }

    /**
     * Subscribes to real-time market data for a contract.
     *
     * @param tickerId unique tick request ID (managed by MarketDataService)
     * @param contract the IBKR contract to subscribe to
     */
    public void reqMktData(int tickerId, Contract contract) {
        if (isConnected()) {
            client.reqMktData(tickerId, contract, "", false, false, null);
            log.debug("Market data requested: tickerId={} symbol={}", tickerId, contract.symbol());
        }
    }

    /**
     * Cancels a market data subscription.
     *
     * @param tickerId the tick request ID to cancel
     */
    public void cancelMktData(int tickerId) {
        if (isConnected()) {
            client.cancelMktData(tickerId);
            log.debug("Market data cancelled: tickerId={}", tickerId);
        }
    }

    /**
     * Requests cancellation of an open order.
     *
     * @param orderId the IBKR order ID to cancel
     */
    public void cancelOrder(int orderId) {
        if (isConnected()) {
            client.cancelOrder(orderId, new OrderCancel());
            log.info("Cancel request sent: ibOrderId={}", orderId);
        } else {
            log.warn("Cannot cancel order {}: not connected to IBKR", orderId);
        }
    }

    // ─── Message Processing Thread ────────────────────────────────────────────

    private void startMessageProcessorThread() {
        EReader reader = new EReader(client, signal);
        reader.start();

        messageProcessorThread = new Thread(() -> {
            log.info("IBKR message processor thread started");
            while (client.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    if (client.isConnected()) {
                        log.error("Error processing IBKR messages: {}", e.getMessage(), e);
                    }
                }
            }
            log.info("IBKR message processor thread stopped");
        }, "ibkr-msg-processor");

        messageProcessorThread.setDaemon(true);
        messageProcessorThread.start();
    }

    private void stopMessageProcessorThread() {
        if (messageProcessorThread != null && messageProcessorThread.isAlive()) {
            messageProcessorThread.interrupt();
        }
    }

    // ─── Status Accessors ────────────────────────────────────────────────────

    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED && client != null && client.isConnected();
    }

    public ConnectionState getState() {
        return state.get();
    }

    public Instant getConnectionTime() {
        return connectionTime.get();
    }

    public Instant getLastHeartbeat() {
        return wrapper != null ? wrapper.getLastMessageTime() : null;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IBConnectionManager");
        scheduler.shutdownNow();
        disconnect();
    }
}
