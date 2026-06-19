package com.tradie.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trade_journal")
public class TradeJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String side;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "realized_pnl_pct", precision = 20, scale = 8)
    private BigDecimal realizedPnlPct;

    @Column(precision = 20, scale = 8)
    private BigDecimal commissions;

    @Column(length = 50)
    private String strategy;

    @Column(length = 10)
    private String timeframe;

    @Column(name = "signal_source", length = 20)
    private String signalSource;

    @Column(name = "setup_quality", length = 20)
    private String setupQuality;

    @Column(name = "execution_quality", length = 20)
    private String executionQuality;

    @Column(columnDefinition = "text")
    private String notes;

    @Convert(converter = TagsConverter.class)
    @Column(columnDefinition = "text")
    private List<String> tags;

    @Column(name = "market_condition", length = 50)
    private String marketCondition;

    @Column(name = "news_context", columnDefinition = "text")
    private String newsContext;

    @Column(name = "entry_chart_url", columnDefinition = "text")
    private String entryChartUrl;

    @Column(name = "exit_chart_url", columnDefinition = "text")
    private String exitChartUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public TradeJournal() {}

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID positionId) { this.positionId = positionId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Instant getEntryTime() { return entryTime; }
    public void setEntryTime(Instant entryTime) { this.entryTime = entryTime; }

    public Instant getExitTime() { return exitTime; }
    public void setExitTime(Instant exitTime) { this.exitTime = exitTime; }

    public BigDecimal getEntryPrice() { return entryPrice; }
    public void setEntryPrice(BigDecimal entryPrice) { this.entryPrice = entryPrice; }

    public BigDecimal getExitPrice() { return exitPrice; }
    public void setExitPrice(BigDecimal exitPrice) { this.exitPrice = exitPrice; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }

    public BigDecimal getRealizedPnlPct() { return realizedPnlPct; }
    public void setRealizedPnlPct(BigDecimal realizedPnlPct) { this.realizedPnlPct = realizedPnlPct; }

    public BigDecimal getCommissions() { return commissions; }
    public void setCommissions(BigDecimal commissions) { this.commissions = commissions; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getSignalSource() { return signalSource; }
    public void setSignalSource(String signalSource) { this.signalSource = signalSource; }

    public String getSetupQuality() { return setupQuality; }
    public void setSetupQuality(String setupQuality) { this.setupQuality = setupQuality; }

    public String getExecutionQuality() { return executionQuality; }
    public void setExecutionQuality(String executionQuality) { this.executionQuality = executionQuality; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getMarketCondition() { return marketCondition; }
    public void setMarketCondition(String marketCondition) { this.marketCondition = marketCondition; }

    public String getNewsContext() { return newsContext; }
    public void setNewsContext(String newsContext) { this.newsContext = newsContext; }

    public String getEntryChartUrl() { return entryChartUrl; }
    public void setEntryChartUrl(String entryChartUrl) { this.entryChartUrl = entryChartUrl; }

    public String getExitChartUrl() { return exitChartUrl; }
    public void setExitChartUrl(String exitChartUrl) { this.exitChartUrl = exitChartUrl; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
