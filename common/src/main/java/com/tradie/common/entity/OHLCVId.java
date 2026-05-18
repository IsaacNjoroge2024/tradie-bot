package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class OHLCVId implements Serializable {

    @Column(nullable = false)
    private Instant time;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(nullable = false, length = 10)
    private String timeframe;

    public OHLCVId() {}

    public OHLCVId(Instant time, String symbol, String exchange, String timeframe) {
        this.time = time;
        this.symbol = symbol;
        this.exchange = exchange;
        this.timeframe = timeframe;
    }

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OHLCVId other)) return false;
        return Objects.equals(time, other.time)
                && Objects.equals(symbol, other.symbol)
                && Objects.equals(exchange, other.exchange)
                && Objects.equals(timeframe, other.timeframe);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, symbol, exchange, timeframe);
    }
}
