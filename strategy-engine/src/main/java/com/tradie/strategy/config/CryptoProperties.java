package com.tradie.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie.crypto")
public class CryptoProperties {

    private boolean enabled = true;
    private String exchange = "PAXOS";
    private boolean skipLowVolumeHours = false;
    private Risk risk = new Risk();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public boolean isSkipLowVolumeHours() { return skipLowVolumeHours; }
    public void setSkipLowVolumeHours(boolean skipLowVolumeHours) {
        this.skipLowVolumeHours = skipLowVolumeHours;
    }

    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }

    public static class Risk {
        private double maxPositionPct = 5.0;
        private double maxTotalExposurePct = 15.0;
        private double minStopLossPct = 3.0;

        public double getMaxPositionPct() { return maxPositionPct; }
        public void setMaxPositionPct(double maxPositionPct) {
            if (maxPositionPct <= 0) throw new IllegalArgumentException("maxPositionPct must be > 0");
            this.maxPositionPct = maxPositionPct;
        }

        public double getMaxTotalExposurePct() { return maxTotalExposurePct; }
        public void setMaxTotalExposurePct(double maxTotalExposurePct) {
            if (maxTotalExposurePct <= 0)
                throw new IllegalArgumentException("maxTotalExposurePct must be > 0");
            this.maxTotalExposurePct = maxTotalExposurePct;
        }

        public double getMinStopLossPct() { return minStopLossPct; }
        public void setMinStopLossPct(double minStopLossPct) {
            if (minStopLossPct < 0) throw new IllegalArgumentException("minStopLossPct must be >= 0");
            this.minStopLossPct = minStopLossPct;
        }
    }
}
