package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "futures_contracts")
public class FuturesContract {

    @Id
    @Column(name = "full_symbol", length = 20)
    private String fullSymbol;

    @Column(name = "symbol", length = 10, nullable = false)
    private String symbol;

    @Column(name = "contract_month", length = 10)
    private String contractMonth;

    @Column(name = "exchange", length = 20, nullable = false)
    private String exchange;

    @Column(name = "multiplier", nullable = false)
    private double multiplier;

    @Column(name = "tick_size", nullable = false)
    private double tickSize;

    @Column(name = "tick_value", nullable = false)
    private double tickValue;

    @Column(name = "first_notice_date")
    private LocalDate firstNoticeDate;

    @Column(name = "last_trade_date")
    private LocalDate lastTradeDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "initial_margin")
    private Double initialMargin;

    @Column(name = "maintenance_margin")
    private Double maintenanceMargin;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_front_month")
    private boolean frontMonth = false;

    public FuturesContract() {}

    public String getFullSymbol() { return fullSymbol; }
    public void setFullSymbol(String fullSymbol) { this.fullSymbol = fullSymbol; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getContractMonth() { return contractMonth; }
    public void setContractMonth(String contractMonth) { this.contractMonth = contractMonth; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

    public double getTickSize() { return tickSize; }
    public void setTickSize(double tickSize) { this.tickSize = tickSize; }

    public double getTickValue() { return tickValue; }
    public void setTickValue(double tickValue) { this.tickValue = tickValue; }

    public LocalDate getFirstNoticeDate() { return firstNoticeDate; }
    public void setFirstNoticeDate(LocalDate firstNoticeDate) { this.firstNoticeDate = firstNoticeDate; }

    public LocalDate getLastTradeDate() { return lastTradeDate; }
    public void setLastTradeDate(LocalDate lastTradeDate) { this.lastTradeDate = lastTradeDate; }

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }

    public Double getInitialMargin() { return initialMargin; }
    public void setInitialMargin(Double initialMargin) { this.initialMargin = initialMargin; }

    public Double getMaintenanceMargin() { return maintenanceMargin; }
    public void setMaintenanceMargin(Double maintenanceMargin) { this.maintenanceMargin = maintenanceMargin; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isFrontMonth() { return frontMonth; }
    public void setFrontMonth(boolean frontMonth) { this.frontMonth = frontMonth; }
}
