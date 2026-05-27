package com.tradie.executor.order;

import com.ib.client.Decimal;
import com.ib.client.Order;
import com.tradie.executor.config.OrderProperties;
import com.tradie.executor.dto.OrderDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds IBKR bracket orders (parent entry + take-profit child + stop-loss child).
 *
 * <p>Bracket sequencing:
 * <ol>
 *   <li>Parent (LMT) – {@code transmit=false}, so all three are held until the SL is sent</li>
 *   <li>Take-profit child (LMT) – {@code transmit=false}</li>
 *   <li>Stop-loss child (STP or STP LMT) – {@code transmit=true}, triggers IBKR to submit all three</li>
 * </ol>
 */
@Component
public class BracketOrderBuilder {

    private final OrderProperties orderProperties;

    public BracketOrderBuilder(OrderProperties orderProperties) {
        this.orderProperties = orderProperties;
    }

    /**
     * Builds the three linked orders that make up a bracket.
     *
     * @param orderDTO the validated order DTO containing entry/SL/TP prices
     * @param parentId the first of three consecutive IBKR order IDs to use
     * @return list of [parent, takeProfit, stopLoss] in submission order
     */
    public List<Order> build(OrderDTO orderDTO, int parentId) {
        String side    = orderDTO.side().name();                                    // "BUY" or "SELL"
        String exitSide = com.tradie.common.entity.Order.OrderSide.BUY.name().equals(side) ? "SELL" : "BUY";

        double quantity   = orderDTO.quantity().doubleValue();
        double entryPrice = orderDTO.limitPrice().doubleValue();
        double tpPrice    = orderDTO.takeProfit().doubleValue();
        double slPrice    = orderDTO.stopLoss().doubleValue();

        Order parent = buildParent(parentId, side, quantity, entryPrice);
        Order takeProfit = buildTakeProfit(parentId + 1, parentId, exitSide, quantity, tpPrice);
        Order stopLoss   = buildStopLoss(parentId + 2, parentId, exitSide, quantity, slPrice);

        return List.of(parent, takeProfit, stopLoss);
    }

    // ─── Private builders ─────────────────────────────────────────────────────

    private Order buildParent(int orderId, String side, double quantity, double entryPrice) {
        Order parent = new Order();
        parent.orderId(orderId);
        parent.action(side);
        parent.orderType("LMT");
        parent.totalQuantity(Decimal.get(quantity));
        parent.lmtPrice(entryPrice);
        parent.transmit(false);
        return parent;
    }

    private Order buildTakeProfit(int orderId, int parentId, String exitSide, double quantity, double tpPrice) {
        Order tp = new Order();
        tp.orderId(orderId);
        tp.parentId(parentId);
        tp.action(exitSide);
        tp.orderType("LMT");
        tp.totalQuantity(Decimal.get(quantity));
        tp.lmtPrice(tpPrice);
        tp.transmit(false);
        return tp;
    }

    private Order buildStopLoss(int orderId, int parentId, String exitSide, double quantity, double slPrice) {
        Order sl = new Order();
        sl.orderId(orderId);
        sl.parentId(parentId);
        sl.action(exitSide);
        sl.totalQuantity(Decimal.get(quantity));
        sl.transmit(true);

        if (orderProperties.isUseStopLimit()) {
            // STP LMT: stop triggers at slPrice, limit at slPrice - offset (for BUY stop, add for SELL stop)
            double limitPrice = "SELL".equals(exitSide)
                    ? slPrice - orderProperties.getStopLimitOffset()
                    : slPrice + orderProperties.getStopLimitOffset();
            sl.orderType("STP LMT");
            sl.auxPrice(slPrice);
            sl.lmtPrice(limitPrice);
        } else {
            sl.orderType("STP");
            sl.auxPrice(slPrice);
        }

        return sl;
    }
}
