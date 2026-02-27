package com.tradie.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_signals")
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalAction action;

    @Column(nullable = false, length = 50)
    private String strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalSource source;

    @Column(nullable = false)
    private Double price;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(length = 10)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalStatus status = SignalStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    public TradeSignal() {}

    // Getters and Setters

    public UUID getId() { return id; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public SignalAction getAction() { return action; }
    public void setAction(SignalAction action) { this.action = action; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public SignalSource getSource() { return source; }
    public void setSource(SignalSource source) { this.source = source; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }

    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public SignalStatus getStatus() { return status; }
    public void setStatus(SignalStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }

    public enum SignalAction {
        BUY, SELL
    }

    public enum SignalSource {
        TRADINGVIEW, INTERNAL, MANUAL
    }

    public enum SignalStatus {
        PENDING, VALIDATED, EXECUTED, REJECTED, EXPIRED
    }
}
