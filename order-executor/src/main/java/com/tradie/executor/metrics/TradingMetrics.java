package com.tradie.executor.metrics;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.ibkr.IBConnectionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centralised trading metrics for the order-executor service.
 *
 * <p>Counters track discrete events (orders submitted/filled, wins/losses).
 * Gauges are backed by live data suppliers so Prometheus always reads the
 * current value without the service having to push updates.
 */
@Component
public class TradingMetrics {

    private final Counter ordersSubmittedBuy;
    private final Counter ordersSubmittedSell;
    private final Counter ordersFilled;
    private final Counter tradeWins;
    private final Counter tradeLosses;
    private final Timer orderLatency;

    // Mutable state for gauges that cannot be computed from a simple supplier
    private final AtomicReference<BigDecimal> dailyPnl = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> totalPnl = new AtomicReference<>(BigDecimal.ZERO);

    public TradingMetrics(MeterRegistry registry,
                          PositionRepository positionRepository,
                          @Lazy IBConnectionManager connectionManager) {

        this.ordersSubmittedBuy = Counter.builder("tradie.orders.submitted")
                .description("Number of bracket orders submitted to IBKR")
                .tag("side", "BUY")
                .register(registry);

        this.ordersSubmittedSell = Counter.builder("tradie.orders.submitted")
                .description("Number of bracket orders submitted to IBKR")
                .tag("side", "SELL")
                .register(registry);

        this.ordersFilled = Counter.builder("tradie.orders.filled")
                .description("Number of entry orders filled by IBKR")
                .register(registry);

        this.tradeWins = Counter.builder("tradie.trades.wins")
                .description("Number of closed positions with positive P&L")
                .register(registry);

        this.tradeLosses = Counter.builder("tradie.trades.losses")
                .description("Number of closed positions with negative P&L")
                .register(registry);

        this.orderLatency = Timer.builder("tradie.orders.latency")
                .description("Time from signal to order fill")
                .register(registry);

        Gauge.builder("tradie.ibkr.connected", connectionManager,
                        mgr -> mgr.isConnected() ? 1.0 : 0.0)
                .description("1 if connected to IBKR, 0 if not")
                .register(registry);

        Gauge.builder("tradie.positions.open", positionRepository,
                        repo -> (double) repo.countByStatus(Position.PositionStatus.OPEN))
                .description("Number of currently open trading positions")
                .register(registry);

        Gauge.builder("tradie.pnl.daily", dailyPnl, ref -> ref.get().doubleValue())
                .description("Cumulative realized P&L for the current trading day")
                .register(registry);

        Gauge.builder("tradie.pnl.total", totalPnl, ref -> ref.get().doubleValue())
                .description("Cumulative realized P&L across all time")
                .register(registry);
    }

    public void orderSubmitted(Order.OrderSide side) {
        if (side == Order.OrderSide.BUY) {
            ordersSubmittedBuy.increment();
        } else {
            ordersSubmittedSell.increment();
        }
    }

    public void orderFilled() {
        ordersFilled.increment();
    }

    public void tradeWin(BigDecimal pnl) {
        tradeWins.increment();
        dailyPnl.updateAndGet(v -> v.add(pnl));
        totalPnl.updateAndGet(v -> v.add(pnl));
    }

    public void tradeLoss(BigDecimal pnl) {
        tradeLosses.increment();
        dailyPnl.updateAndGet(v -> v.add(pnl));
        totalPnl.updateAndGet(v -> v.add(pnl));
    }

    public void recordOrderLatency(Instant signalTime) {
        if (signalTime != null) {
            long millis = Duration.between(signalTime, Instant.now()).toMillis();
            orderLatency.record(Duration.ofMillis(millis));
        }
    }

    public void resetDailyPnl() {
        dailyPnl.set(BigDecimal.ZERO);
    }
}
