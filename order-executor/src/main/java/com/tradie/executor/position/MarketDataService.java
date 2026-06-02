package com.tradie.executor.position;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ib.client.Contract;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.ibkr.IBConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages real-time market data subscriptions for open positions.
 * Receives price updates via IBKR tickPrice callbacks, caches to Redis,
 * and triggers P&L recalculation, trailing stop, and break-even checks.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final int TICK_ID_BASE = 5000;
    private static final int FIELD_BID    = 1;
    private static final int FIELD_ASK    = 2;
    private static final int FIELD_LAST   = 4;

    private final PositionRepository positionRepository;
    private final PnLCalculator pnlCalculator;
    private final TrailingStopManager trailingStopManager;
    private final BreakEvenManager breakEvenManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // tickerId → symbol
    private final ConcurrentHashMap<Integer, String> tickToSymbol = new ConcurrentHashMap<>();
    // symbol → latest effective price
    private final ConcurrentHashMap<String, BigDecimal> latestPrice = new ConcurrentHashMap<>();
    // symbol → bid/ask/last snapshot
    private final ConcurrentHashMap<String, PriceSnapshot> priceSnapshots = new ConcurrentHashMap<>();

    private final AtomicInteger tickIdCounter = new AtomicInteger(TICK_ID_BASE);

    // Lazy to break circular dependency with IBConnectionManager
    private volatile IBConnectionManager connectionManager;

    public MarketDataService(
            PositionRepository positionRepository,
            PnLCalculator pnlCalculator,
            TrailingStopManager trailingStopManager,
            BreakEvenManager breakEvenManager,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.positionRepository = positionRepository;
        this.pnlCalculator = pnlCalculator;
        this.trailingStopManager = trailingStopManager;
        this.breakEvenManager = breakEvenManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Called by IBConnectionManager during init to wire the back-reference.
     * Propagates to TrailingStopManager and BreakEvenManager which also need it.
     */
    public void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        trailingStopManager.setConnectionManager(connectionManager);
        breakEvenManager.setConnectionManager(connectionManager);
    }

    // ─── Subscription Management ──────────────────────────────────────────────

    /**
     * Called by IBConnectionManager after IBKR connection is established.
     * Subscribes to live prices for all currently open positions.
     */
    public void subscribeToOpenPositions() {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        log.info("Subscribing to market data for {} open positions", openPositions.size());
        openPositions.forEach(this::subscribeToPosition);
    }

    /**
     * Subscribes to live price feed for a single position.
     */
    public void subscribeToPosition(Position position) {
        if (connectionManager == null || !connectionManager.isConnected()) return;
        String symbol = position.getSymbol();
        if (tickToSymbol.containsValue(symbol)) {
            log.debug("Already subscribed to market data for {}", symbol);
            return;
        }
        int tickId = tickIdCounter.getAndIncrement();
        tickToSymbol.put(tickId, symbol);
        connectionManager.reqMktData(tickId, buildContract(position));
        log.info("Market data subscribed: symbol={} tickId={}", symbol, tickId);
    }

    /**
     * Cancels all active market data subscriptions (called on disconnect).
     */
    public void cancelAllSubscriptions() {
        if (connectionManager == null) return;
        tickToSymbol.forEach((tickId, symbol) -> {
            connectionManager.cancelMktData(tickId);
            log.debug("Market data cancelled: symbol={} tickId={}", symbol, tickId);
        });
        tickToSymbol.clear();
    }

    // ─── Price Callback ───────────────────────────────────────────────────────

    /**
     * Called by TradieEWrapper when a tickPrice callback is received from IBKR.
     */
    public void onTickPrice(int tickerId, int field, double price) {
        String symbol = tickToSymbol.get(tickerId);
        if (symbol == null || price <= 0) return;

        PriceSnapshot snapshot = priceSnapshots.computeIfAbsent(symbol, k -> new PriceSnapshot());
        if (field == FIELD_BID)  snapshot.bid  = price;
        if (field == FIELD_ASK)  snapshot.ask  = price;
        if (field == FIELD_LAST) snapshot.last = price;

        double effectivePrice = snapshot.last > 0 ? snapshot.last
                : (snapshot.bid > 0 && snapshot.ask > 0 ? (snapshot.bid + snapshot.ask) / 2.0 : 0);
        if (effectivePrice <= 0) return;

        BigDecimal current = BigDecimal.valueOf(effectivePrice);
        latestPrice.put(symbol, current);
        cacheToRedis(symbol, snapshot);
        updateOpenPositions(symbol, current);
    }

    /**
     * Returns the latest cached price for a symbol, or null if unknown.
     */
    public BigDecimal getLatestPrice(String symbol) {
        return latestPrice.get(symbol);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private void updateOpenPositions(String symbol, BigDecimal currentPrice) {
        List<Position> positions = positionRepository.findByStatusAndSymbol(
                Position.PositionStatus.OPEN, symbol);
        for (Position position : positions) {
            position.setUnrealizedPnl(pnlCalculator.unrealizedPnl(position, currentPrice));
            positionRepository.save(position);
            trailingStopManager.checkAndAdjust(position, currentPrice);
            breakEvenManager.checkAndActivate(position, currentPrice);
        }
    }

    private void cacheToRedis(String symbol, PriceSnapshot snapshot) {
        try {
            String key = "position:" + symbol + ":price";
            String json = objectMapper.writeValueAsString(
                    Map.of("bid", snapshot.bid, "ask", snapshot.ask, "last", snapshot.last));
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("Failed to cache price to Redis for {}: {}", symbol, e.getMessage());
        }
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

    static class PriceSnapshot {
        volatile double bid  = 0.0;
        volatile double ask  = 0.0;
        volatile double last = 0.0;
    }
}

