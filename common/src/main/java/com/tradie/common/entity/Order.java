package com.tradie.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
    @Column(nullable = false, length = 20)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 20, scale = 8)
    private BigDecimal limitPrice;

    @Column(name = "stop_price", precision = 20, scale = 8)
    private BigDecimal stopPrice;

    @Column(name = "parent_order_id")
    private UUID parentOrderId;

    @Column(name = "is_bracket_parent")
    private Boolean isBracketParent = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "filled_quantity", precision = 20, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "avg_fill_price", precision = 20, scale = 8)
    private BigDecimal avgFillPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal commission;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public Order() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

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

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }

    public BigDecimal getStopPrice() { return stopPrice; }
    public void setStopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; }

    public UUID getParentOrderId() { return parentOrderId; }
    public void setParentOrderId(UUID parentOrderId) { this.parentOrderId = parentOrderId; }

    public Boolean getIsBracketParent() { return isBracketParent; }
    public void setIsBracketParent(Boolean isBracketParent) { this.isBracketParent = isBracketParent; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(BigDecimal filledQuantity) { this.filledQuantity = filledQuantity; }

    public BigDecimal getAvgFillPrice() { return avgFillPrice; }
    public void setAvgFillPrice(BigDecimal avgFillPrice) { this.avgFillPrice = avgFillPrice; }

    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }

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
