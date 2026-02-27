package com.tradie.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(name = "asset_class", nullable = false, length = 20)
    private String assetClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Order.OrderSide side;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "entry_price", nullable = false)
    private Double entryPrice;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column(name = "trailing_stop_pct")
    private Double trailingStopPct;

    @Column(name = "realized_pnl")
    private Double realizedPnl;

    @Column(name = "unrealized_pnl")
    private Double unrealizedPnl;

    @Column(name = "commission_total")
    private Double commissionTotal = 0.0;

    @Column(length = 50)
    private String strategy;

    @Column(name = "entry_signal_id")
    private UUID entrySignalId;

    @Column(name = "exit_signal_id")
    private UUID exitSignalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status = PositionStatus.OPEN;

    public Position() {}

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

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }

    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }

    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }

    public Double getTrailingStopPct() { return trailingStopPct; }
    public void setTrailingStopPct(Double trailingStopPct) { this.trailingStopPct = trailingStopPct; }

    public Double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(Double realizedPnl) { this.realizedPnl = realizedPnl; }

    public Double getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(Double unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }

    public Double getCommissionTotal() { return commissionTotal; }
    public void setCommissionTotal(Double commissionTotal) { this.commissionTotal = commissionTotal; }

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
