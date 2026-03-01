package com.tradie.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Order.OrderSide side;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Column(name = "trailing_stop_pct")
    private Double trailingStopPct;

    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "unrealized_pnl", precision = 20, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name = "commission_total", precision = 20, scale = 8)
    private BigDecimal commissionTotal = BigDecimal.ZERO;

    @Column(length = 50)
    private String strategy;

    @Column(name = "entry_signal_id")
    private UUID entrySignalId;

    @Column(name = "exit_signal_id")
    private UUID exitSignalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionStatus status = PositionStatus.OPEN;

    public Position() {}

    @PrePersist
    protected void onOpen() {
        if (openedAt == null) {
            openedAt = Instant.now();
        }
    }

    // Getters and Setters

    public UUID getId() { return id; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }

    public Order.OrderSide getSide() { return side; }
    public void setSide(Order.OrderSide side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }

    public BigDecimal getExitPrice() { return exitPrice; }
    public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }

    public BigDecimal getStopLoss() { return stopLoss; }
    public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }

    public BigDecimal getTakeProfit() { return takeProfit; }
    public void setTakeProfit(BigDecimal takeProfit) { this.takeProfit = takeProfit; }

    public Double getTrailingStopPct() { return trailingStopPct; }
    public void setTrailingStopPct(Double trailingStopPct) { this.trailingStopPct = trailingStopPct; }

    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }

    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }

    public BigDecimal getCommissionTotal() { return commissionTotal; }
    public void setCommissionTotal(BigDecimal commissionTotal) { this.commissionTotal = commissionTotal; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public UUID getEntrySignalId() { return entrySignalId; }
    public void setEntrySignalId(UUID entrySignalId) { this.entrySignalId = entrySignalId; }

    public UUID getExitSignalId() { return exitSignalId; }
    public void setExitSignalId(UUID exitSignalId) { this.exitSignalId = exitSignalId; }

    public PositionStatus getStatus() { return status; }
    public void setStatus(PositionStatus status) { this.status = status; }

    public enum PositionStatus {
        OPEN, CLOSED
    }
}
