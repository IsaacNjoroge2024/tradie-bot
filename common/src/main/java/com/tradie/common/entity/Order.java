package com.tradie.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "signal_id")
    private UUID signalId;

    @Column(name = "ib_order_id")
    private Integer ibOrderId;

    @Column(name = "ib_perm_id")
    private Long ibPermId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "limit_price")
    private Double limitPrice;

    @Column(name = "stop_price")
    private Double stopPrice;

    @Column(name = "parent_order_id")
    private UUID parentOrderId;

    @Column(name = "is_bracket_parent")
    private Boolean isBracketParent = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "filled_quantity")
    private Double filledQuantity = 0.0;

    @Column(name = "avg_fill_price")
    private Double avgFillPrice;

    @Column
    private Double commission;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Order() {}

    // Getters and Setters

    public UUID getId() { return id; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getSignalId() { return signalId; }
    public void setSignalId(UUID signalId) { this.signalId = signalId; }

    public Integer getIbOrderId() { return ibOrderId; }
    public void setIbOrderId(Integer ibOrderId) { this.ibOrderId = ibOrderId; }

    public Long getIbPermId() { return ibPermId; }
    public void setIbPermId(Long ibPermId) { this.ibPermId = ibPermId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getLimitPrice() { return limitPrice; }
    public void setLimitPrice(Double limitPrice) { this.limitPrice = limitPrice; }

    public Double getStopPrice() { return stopPrice; }
    public void setStopPrice(Double stopPrice) { this.stopPrice = stopPrice; }

    public UUID getParentOrderId() { return parentOrderId; }
    public void setParentOrderId(UUID parentOrderId) { this.parentOrderId = parentOrderId; }

    public Boolean getIsBracketParent() { return isBracketParent; }
    public void setIsBracketParent(Boolean isBracketParent) { this.isBracketParent = isBracketParent; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Double getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(Double filledQuantity) { this.filledQuantity = filledQuantity; }

    public Double getAvgFillPrice() { return avgFillPrice; }
    public void setAvgFillPrice(Double avgFillPrice) { this.avgFillPrice = avgFillPrice; }

    public Double getCommission() { return commission; }
    public void setCommission(Double commission) { this.commission = commission; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getFilledAt() { return filledAt; }
    public void setFilledAt(Instant filledAt) { this.filledAt = filledAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public enum OrderSide {
        BUY, SELL
    }

    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT
    }

    public enum OrderStatus {
        PENDING, SUBMITTED, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED
    }
}
