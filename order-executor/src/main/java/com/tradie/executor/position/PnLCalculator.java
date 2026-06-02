package com.tradie.executor.position;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates unrealized and realized P&L for positions.
 */
@Component
public class PnLCalculator {

    /**
     * Unrealized P&L: (currentPrice - entryPrice) * quantity * direction
     * Direction: +1 for LONG (BUY), -1 for SHORT (SELL)
     */
    public BigDecimal unrealizedPnl(Position position, BigDecimal currentPrice) {
        BigDecimal diff = currentPrice.subtract(position.getEntryPrice());
        BigDecimal pnl = diff.multiply(position.getQuantity());
        if (position.getSide() == Order.OrderSide.SELL) {
            pnl = pnl.negate();
        }
        return pnl.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Realized P&L: (exitPrice - entryPrice) * quantity * direction - commissions
     */
    public BigDecimal realizedPnl(Position position, BigDecimal exitPrice) {
        BigDecimal diff = exitPrice.subtract(position.getEntryPrice());
        BigDecimal pnl = diff.multiply(position.getQuantity());
        if (position.getSide() == Order.OrderSide.SELL) {
            pnl = pnl.negate();
        }
        BigDecimal commission = position.getCommissionTotal() != null
                ? position.getCommissionTotal() : BigDecimal.ZERO;
        return pnl.subtract(commission).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * P&L as a percentage of position cost: unrealizedPnL / (entryPrice * quantity) * 100
     */
    public double unrealizedPnlPct(Position position, BigDecimal currentPrice) {
        BigDecimal cost = position.getEntryPrice().multiply(position.getQuantity());
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        BigDecimal pnl = unrealizedPnl(position, currentPrice);
        return pnl.divide(cost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
    }
}
