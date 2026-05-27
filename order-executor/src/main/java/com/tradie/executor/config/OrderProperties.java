package com.tradie.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie.orders")
public class OrderProperties {

    private String defaultOrderType = "LMT";
    private boolean useStopLimit = false;
    private double stopLimitOffset = 0.10;
    private int orderTimeoutSeconds = 300;
    private int cancelUnfilledAfterSeconds = 3600;
    private boolean bracketEnabled = true;
    private boolean stopLossRequired = true;
    private boolean takeProfitRequired = true;

    public String getDefaultOrderType() { return defaultOrderType; }
    public void setDefaultOrderType(String defaultOrderType) { this.defaultOrderType = defaultOrderType; }

    public boolean isUseStopLimit() { return useStopLimit; }
    public void setUseStopLimit(boolean useStopLimit) { this.useStopLimit = useStopLimit; }

    public double getStopLimitOffset() { return stopLimitOffset; }
    public void setStopLimitOffset(double stopLimitOffset) { this.stopLimitOffset = stopLimitOffset; }

    public int getOrderTimeoutSeconds() { return orderTimeoutSeconds; }
    public void setOrderTimeoutSeconds(int orderTimeoutSeconds) { this.orderTimeoutSeconds = orderTimeoutSeconds; }

    public int getCancelUnfilledAfterSeconds() { return cancelUnfilledAfterSeconds; }
    public void setCancelUnfilledAfterSeconds(int cancelUnfilledAfterSeconds) {
        this.cancelUnfilledAfterSeconds = cancelUnfilledAfterSeconds;
    }

    public boolean isBracketEnabled() { return bracketEnabled; }
    public void setBracketEnabled(boolean bracketEnabled) { this.bracketEnabled = bracketEnabled; }

    public boolean isStopLossRequired() { return stopLossRequired; }
    public void setStopLossRequired(boolean stopLossRequired) { this.stopLossRequired = stopLossRequired; }

    public boolean isTakeProfitRequired() { return takeProfitRequired; }
    public void setTakeProfitRequired(boolean takeProfitRequired) { this.takeProfitRequired = takeProfitRequired; }
}
