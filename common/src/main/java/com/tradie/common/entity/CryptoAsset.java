package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "crypto_assets")
public class CryptoAsset {

    @Id
    @Column(name = "symbol", length = 10)
    private String symbol;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "min_order_size", nullable = false)
    private double minOrderSize;

    @Column(name = "size_increment", nullable = false)
    private double sizeIncrement;

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

    public double getMinOrderSize() { return minOrderSize; }
    public void setMinOrderSize(double minOrderSize) { this.minOrderSize = minOrderSize; }

    public double getSizeIncrement() { return sizeIncrement; }
    public void setSizeIncrement(double sizeIncrement) { this.sizeIncrement = sizeIncrement; }

    public int getPricePrecision() { return pricePrecision; }
    public void setPricePrecision(int pricePrecision) { this.pricePrecision = pricePrecision; }

    public double getVolatilityMultiplier() { return volatilityMultiplier; }
    public void setVolatilityMultiplier(double volatilityMultiplier) {
        this.volatilityMultiplier = volatilityMultiplier;
    }

    public boolean isAvailableOnIbkr() { return availableOnIbkr; }
    public void setAvailableOnIbkr(boolean availableOnIbkr) { this.availableOnIbkr = availableOnIbkr; }
}
