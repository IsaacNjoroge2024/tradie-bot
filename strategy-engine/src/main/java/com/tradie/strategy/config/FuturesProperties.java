package com.tradie.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie.futures")
public class FuturesProperties {

    private int defaultRolloverDays = 8;
    private boolean autoRollPositions = false;
    private double marginBufferPct = 20.0;
    private boolean updateSpecsDaily = true;

    public int getDefaultRolloverDays() { return defaultRolloverDays; }
    public void setDefaultRolloverDays(int defaultRolloverDays) {
        if (defaultRolloverDays < 0) {
            throw new IllegalArgumentException("defaultRolloverDays must be >= 0");
        }
        this.defaultRolloverDays = defaultRolloverDays;
    }

    public boolean isAutoRollPositions() { return autoRollPositions; }
    public void setAutoRollPositions(boolean autoRollPositions) {
        this.autoRollPositions = autoRollPositions;
    }

    public double getMarginBufferPct() { return marginBufferPct; }
    public void setMarginBufferPct(double marginBufferPct) {
        if (!Double.isFinite(marginBufferPct) || marginBufferPct < 0 || marginBufferPct > 100) {
            throw new IllegalArgumentException("marginBufferPct must be finite and between 0 and 100");
        }
        this.marginBufferPct = marginBufferPct;
    }

    public boolean isUpdateSpecsDaily() { return updateSpecsDaily; }
    public void setUpdateSpecsDaily(boolean updateSpecsDaily) {
        this.updateSpecsDaily = updateSpecsDaily;
    }
}
