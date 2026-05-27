package com.tradie.executor.order;

import com.ib.client.Order;
import com.tradie.executor.config.OrderProperties;
import com.tradie.executor.dto.OrderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class BracketOrderBuilderTest {

    private BracketOrderBuilder bracketOrderBuilder;
    private OrderProperties orderProperties;

    @BeforeEach
    void setUp() {
        orderProperties = new OrderProperties();
        bracketOrderBuilder = new BracketOrderBuilder(orderProperties);
    }

    @Test
    void build_buyOrder_returnsThreeOrders() {
        List<Order> orders = bracketOrderBuilder.build(buildBuyOrder(), 100);

        assertThat(orders).hasSize(3);
    }

    @Test
    void build_parentOrder_isLimitBuyNotTransmitted() {
        Order parent = bracketOrderBuilder.build(buildBuyOrder(), 100).get(0);

        assertThat(parent.orderId()).isEqualTo(100);
        assertThat(parent.action().getApiString()).isEqualTo("BUY");
        assertThat(parent.orderType().getApiString()).isEqualTo("LMT");
        assertThat(parent.lmtPrice()).isEqualTo(150.00);
        assertThat(parent.transmit()).isFalse();
    }

    @Test
    void build_takeProfitOrder_isLimitSellLinkedToParent() {
        Order tp = bracketOrderBuilder.build(buildBuyOrder(), 100).get(1);

        assertThat(tp.orderId()).isEqualTo(101);
        assertThat(tp.parentId()).isEqualTo(100);
        assertThat(tp.action().getApiString()).isEqualTo("SELL");
        assertThat(tp.orderType().getApiString()).isEqualTo("LMT");
        assertThat(tp.lmtPrice()).isEqualTo(165.00);
        assertThat(tp.transmit()).isFalse();
    }

    @Test
    void build_stopLossOrder_isStopSellTransmitted() {
        Order sl = bracketOrderBuilder.build(buildBuyOrder(), 100).get(2);

        assertThat(sl.orderId()).isEqualTo(102);
        assertThat(sl.parentId()).isEqualTo(100);
        assertThat(sl.action().getApiString()).isEqualTo("SELL");
        assertThat(sl.orderType().getApiString()).isEqualTo("STP");
        assertThat(sl.auxPrice()).isEqualTo(140.00);
        assertThat(sl.transmit()).isTrue();
    }

    @Test
    void build_sellOrder_exitSideIsBuy() {
        List<Order> orders = bracketOrderBuilder.build(buildSellOrder(), 200);

        assertThat(orders.get(0).action().getApiString()).isEqualTo("SELL");
        assertThat(orders.get(1).action().getApiString()).isEqualTo("BUY");
        assertThat(orders.get(2).action().getApiString()).isEqualTo("BUY");
    }

    @Test
    void build_withStopLimit_stopLossIsStpLmt() {
        orderProperties.setUseStopLimit(true);
        orderProperties.setStopLimitOffset(0.10);

        Order sl = bracketOrderBuilder.build(buildBuyOrder(), 100).get(2);

        assertThat(sl.orderType().getApiString()).isEqualTo("STP LMT");
        assertThat(sl.auxPrice()).isEqualTo(140.00);
        // SELL exit stop: limit = slPrice - offset
        assertThat(sl.lmtPrice()).isCloseTo(139.90, offset(0.001));
    }

    @Test
    void build_orderIdsAreConsecutive() {
        List<Order> orders = bracketOrderBuilder.build(buildBuyOrder(), 500);

        assertThat(orders.get(0).orderId()).isEqualTo(500);
        assertThat(orders.get(1).orderId()).isEqualTo(501);
        assertThat(orders.get(2).orderId()).isEqualTo(502);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private OrderDTO buildBuyOrder() {
        return new OrderDTO(
                UUID.randomUUID(), "AAPL", "NASDAQ", "STK",
                com.tradie.common.entity.Order.OrderSide.BUY,
                com.tradie.common.entity.Order.OrderType.LIMIT,
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(140.00), BigDecimal.valueOf(165.00),
                "FVG", Instant.now().plusSeconds(300),
                BigDecimal.valueOf(300), 2.0, 4.0, 6.0,
                BigDecimal.valueOf(600), 2.0, "FIXED_FRACTIONAL"
        );
    }

    private OrderDTO buildSellOrder() {
        return new OrderDTO(
                UUID.randomUUID(), "AAPL", "NASDAQ", "STK",
                com.tradie.common.entity.Order.OrderSide.SELL,
                com.tradie.common.entity.Order.OrderType.LIMIT,
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(160.00), BigDecimal.valueOf(135.00),
                "FVG", Instant.now().plusSeconds(300),
                BigDecimal.valueOf(300), 2.0, 4.0, 6.0,
                BigDecimal.valueOf(600), 2.0, "FIXED_FRACTIONAL"
        );
    }
}
