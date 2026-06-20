package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currency_pairs")
public class CurrencyPair {

    @Id
    @Column(name = "symbol", length = 10)
    private String symbol;

    @Column(name = "base_currency", length = 5, nullable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", length = 5, nullable = false)
    private String quoteCurrency;

    @Column(name = "pip_position", nullable = false)
    private int pipPosition;

    @Column(name = "pip_value_per_standard_lot", nullable = false)
    private double pipValuePerStandardLot;

    @Column(name = "min_lot_size", nullable = false)
    private double minLotSize;

    @Column(name = "lot_step", nullable = false)
    private double lotStep;

    @Column(name = "margin_rate", nullable = false)
    private double marginRate;

    @Column(name = "category", length = 20, nullable = false)
    private String category;

    public CurrencyPair() {}

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }

    public String getQuoteCurrency() { return quoteCurrency; }
    public void setQuoteCurrency(String quoteCurrency) { this.quoteCurrency = quoteCurrency; }

    public int getPipPosition() { return pipPosition; }
    public void setPipPosition(int pipPosition) { this.pipPosition = pipPosition; }

    public double getPipValuePerStandardLot() { return pipValuePerStandardLot; }
    public void setPipValuePerStandardLot(double pipValuePerStandardLot) {
        this.pipValuePerStandardLot = pipValuePerStandardLot;
    }

    public double getMinLotSize() { return minLotSize; }
    public void setMinLotSize(double minLotSize) { this.minLotSize = minLotSize; }

    public double getLotStep() { return lotStep; }
    public void setLotStep(double lotStep) { this.lotStep = lotStep; }

    public double getMarginRate() { return marginRate; }
    public void setMarginRate(double marginRate) { this.marginRate = marginRate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
