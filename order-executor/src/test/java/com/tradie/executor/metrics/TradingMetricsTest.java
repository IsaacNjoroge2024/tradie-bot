package com.tradie.executor.metrics;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.executor.ibkr.IBConnectionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingMetricsTest {

    @Mock private PositionRepository positionRepository;
    @Mock private IBConnectionManager connectionManager;

    private SimpleMeterRegistry registry;
    private TradingMetrics tradingMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        // Gauge suppliers are registered but not evaluated at construction time —
        // no stubs needed here; default mock returns (false/0L) are safe.
        tradingMetrics = new TradingMetrics(registry, positionRepository, connectionManager);
    }

    // ─── orderSubmitted ───────────────────────────────────────────────────────

    @Test
    void orderSubmitted_buy_incrementsBuyCounter() {
        tradingMetrics.orderSubmitted(Order.OrderSide.BUY);

        Counter counter = registry.find("tradie.orders.submitted").tag("side", "BUY").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void orderSubmitted_sell_incrementsSellCounter() {
        tradingMetrics.orderSubmitted(Order.OrderSide.SELL);

        Counter counter = registry.find("tradie.orders.submitted").tag("side", "SELL").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ─── orderFilled ──────────────────────────────────────────────────────────

    @Test
    void orderFilled_incrementsFilledCounter() {
        tradingMetrics.orderFilled();
        tradingMetrics.orderFilled();

        Counter counter = registry.find("tradie.orders.filled").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    // ─── tradeWin / tradeLoss ─────────────────────────────────────────────────

    @Test
    void tradeWin_incrementsWinCounterAndUpdatesPnl() {
        tradingMetrics.tradeWin(BigDecimal.valueOf(500.0));

        Counter wins = registry.find("tradie.trades.wins").counter();
        assertThat(wins).isNotNull();
        assertThat(wins.count()).isEqualTo(1.0);

        Gauge daily = registry.find("tradie.pnl.daily").gauge();
        assertThat(daily).isNotNull();
        assertThat(daily.value()).isEqualTo(500.0);

        Gauge total = registry.find("tradie.pnl.total").gauge();
        assertThat(total).isNotNull();
        assertThat(total.value()).isEqualTo(500.0);
    }

    @Test
    void tradeLoss_incrementsLossCounterAndUpdatesPnl() {
        tradingMetrics.tradeLoss(BigDecimal.valueOf(-300.0));

        Counter losses = registry.find("tradie.trades.losses").counter();
        assertThat(losses).isNotNull();
        assertThat(losses.count()).isEqualTo(1.0);

        Gauge daily = registry.find("tradie.pnl.daily").gauge();
        assertThat(daily).isNotNull();
        assertThat(daily.value()).isEqualTo(-300.0);
    }

    @Test
    void tradeWinAndLoss_accumulatePnl() {
        tradingMetrics.tradeWin(BigDecimal.valueOf(1000.0));
        tradingMetrics.tradeLoss(BigDecimal.valueOf(-400.0));

        Gauge total = registry.find("tradie.pnl.total").gauge();
        assertThat(total).isNotNull();
        assertThat(total.value()).isEqualTo(600.0);
    }

    // ─── resetDailyPnl ────────────────────────────────────────────────────────

    @Test
    void resetDailyPnl_setsGaugeToZero() {
        tradingMetrics.tradeWin(BigDecimal.valueOf(200.0));
        tradingMetrics.resetDailyPnl();

        Gauge daily = registry.find("tradie.pnl.daily").gauge();
        assertThat(daily).isNotNull();
        assertThat(daily.value()).isEqualTo(0.0);

        // Total P&L is not reset
        Gauge total = registry.find("tradie.pnl.total").gauge();
        assertThat(total).isNotNull();
        assertThat(total.value()).isEqualTo(200.0);
    }

    // ─── recordOrderLatency ───────────────────────────────────────────────────

    @Test
    void recordOrderLatency_nullSignalTime_doesNotThrow() {
        tradingMetrics.recordOrderLatency(null);

        Timer timer = registry.find("tradie.orders.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(0);
    }

    @Test
    void recordOrderLatency_validSignalTime_recordsTimer() {
        Instant signalTime = Instant.now().minusSeconds(2);
        tradingMetrics.recordOrderLatency(signalTime);

        Timer timer = registry.find("tradie.orders.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    // ─── Gauges ───────────────────────────────────────────────────────────────

    @Test
    void ibkrConnectedGauge_reflectsConnectionManagerState() {
        when(connectionManager.isConnected()).thenReturn(true);

        Gauge gauge = registry.find("tradie.ibkr.connected").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);
    }

    @Test
    void openPositionsGauge_reflectsRepositoryCount() {
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(3L);

        Gauge gauge = registry.find("tradie.positions.open").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(3.0);
    }
}
