package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "crypto_assets")
public class CryptoAsset {

    @Id
    @Column(name = "symbol", length = 10)
    private String symbol;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "min_order_size", nullable = false, precision = 20, scale = 8)
    private BigDecimal minOrderSize;

    @Column(name = "size_increment", nullable = false, precision = 20, scale = 8)
    private BigDecimal sizeIncrement;

    @Column(name = "price_precision", nullable = false)
    private int pricePrecision;

    @Column(name = "volatility_multiplier", nullable = false)
    private double volatilityMultiplier;

    @Column(name = "available_on_ibkr", nullable = false)
    private boolean availableOnIbkr = true;

    public CryptoAsset() {}

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getMinOrderSize() { return minOrderSize; }
    public void setMinOrderSize(BigDecimal minOrderSize) { this.minOrderSize = minOrderSize; }

    public BigDecimal getSizeIncrement() { return sizeIncrement; }
    public void setSizeIncrement(BigDecimal sizeIncrement) { this.sizeIncrement = sizeIncrement; }

    public int getPricePrecision() { return pricePrecision; }
    public void setPricePrecision(int pricePrecision) { this.pricePrecision = pricePrecision; }

    public double getVolatilityMultiplier() { return volatilityMultiplier; }
    public void setVolatilityMultiplier(double volatilityMultiplier) {
        this.volatilityMultiplier = volatilityMultiplier;
    }

    public boolean isAvailableOnIbkr() { return availableOnIbkr; }
    public void setAvailableOnIbkr(boolean availableOnIbkr) { this.availableOnIbkr = availableOnIbkr; }
}
