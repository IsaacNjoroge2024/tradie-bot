package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PnLCalculatorTest {

    private PnLCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PnLCalculator();
    }

    // ─── Unrealized P&L ───────────────────────────────────────────────────────

    @Test
    void unrealizedPnl_longPosition_profitableMove() {
        Position position = buildPosition(Order.OrderSide.BUY, 150.00, 10);

        BigDecimal result = calculator.unrealizedPnl(position, BigDecimal.valueOf(155.00));

        // (155 - 150) * 10 = 50
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(50.0));
    }

    @Test
    void unrealizedPnl_longPosition_losingMove() {
        Position position = buildPosition(Order.OrderSide.BUY, 150.00, 10);

        BigDecimal result = calculator.unrealizedPnl(position, BigDecimal.valueOf(145.00));

        // (145 - 150) * 10 = -50
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(-50.0));
    }

    @Test
    void unrealizedPnl_shortPosition_profitableMove() {
        Position position = buildPosition(Order.OrderSide.SELL, 150.00, 10);

        // Price drops — SHORT profits
        BigDecimal result = calculator.unrealizedPnl(position, BigDecimal.valueOf(145.00));

        // (145 - 150) * 10 * -1 = 50
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(50.0));
    }

    @Test
    void unrealizedPnl_shortPosition_losingMove() {
        Position position = buildPosition(Order.OrderSide.SELL, 150.00, 10);

        BigDecimal result = calculator.unrealizedPnl(position, BigDecimal.valueOf(155.00));

        // (155 - 150) * 10 * -1 = -50
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(-50.0));
    }

    // ─── Realized P&L ─────────────────────────────────────────────────────────

    @Test
    void realizedPnl_longPosition_withCommission() {
        Position position = buildPosition(Order.OrderSide.BUY, 150.00, 10);
        position.setCommissionTotal(BigDecimal.valueOf(5.00));

        BigDecimal result = calculator.realizedPnl(position, BigDecimal.valueOf(160.00));

        // (160 - 150) * 10 - 5 = 95
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(95.0));
    }

    @Test
    void realizedPnl_noCommission_usesZero() {
        Position position = buildPosition(Order.OrderSide.BUY, 100.00, 5);
        position.setCommissionTotal(null);

        BigDecimal result = calculator.realizedPnl(position, BigDecimal.valueOf(110.00));

        // (110 - 100) * 5 - 0 = 50
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(50.0));
    }

    // ─── P&L Percentage ───────────────────────────────────────────────────────

    @Test
    void unrealizedPnlPct_longPosition_returnsCorrectPct() {
        Position position = buildPosition(Order.OrderSide.BUY, 100.00, 10);

        double pct = calculator.unrealizedPnlPct(position, BigDecimal.valueOf(102.00));

        // (102-100)*10 / (100*10) * 100 = 2%
        assertThat(pct).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void unrealizedPnlPct_zeroCost_returnsZero() {
        Position position = buildPosition(Order.OrderSide.BUY, 0.0, 10);

        double pct = calculator.unrealizedPnlPct(position, BigDecimal.valueOf(5.00));

        assertThat(pct).isEqualTo(0.0);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Position buildPosition(Order.OrderSide side, double entryPrice, int quantity) {
        Position position = new Position();
        position.setSide(side);
        position.setEntryPrice(BigDecimal.valueOf(entryPrice));
        position.setQuantity(BigDecimal.valueOf(quantity));
        position.setCommissionTotal(BigDecimal.ZERO);
        position.setStatus(Position.PositionStatus.OPEN);
        return position;
    }
}
