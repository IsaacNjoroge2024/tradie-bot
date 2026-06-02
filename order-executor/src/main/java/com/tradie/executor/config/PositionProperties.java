package com.tradie.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie")
public class PositionProperties {

    private Positions positions = new Positions();
    private TrailingStop trailingStop = new TrailingStop();
    private BreakEven breakEven = new BreakEven();

    public Positions getPositions() { return positions; }
    public void setPositions(Positions positions) { this.positions = positions; }

    public TrailingStop getTrailingStop() { return trailingStop; }
    public void setTrailingStop(TrailingStop trailingStop) { this.trailingStop = trailingStop; }

    public BreakEven getBreakEven() { return breakEven; }
    public void setBreakEven(BreakEven breakEven) { this.breakEven = breakEven; }

    public static class Positions {
        private int syncIntervalSeconds = 300;
        private int priceUpdateIntervalMs = 1000;

        public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
        public void setSyncIntervalSeconds(int syncIntervalSeconds) { this.syncIntervalSeconds = syncIntervalSeconds; }

        public int getPriceUpdateIntervalMs() { return priceUpdateIntervalMs; }
        public void setPriceUpdateIntervalMs(int priceUpdateIntervalMs) { this.priceUpdateIntervalMs = priceUpdateIntervalMs; }
    }

    public static class TrailingStop {
        private boolean enabled = true;
        private double defaultTrailPct = 2.0;
        private double activationPct = 1.0;
        private double stepPct = 0.25;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getDefaultTrailPct() { return defaultTrailPct; }
        public void setDefaultTrailPct(double defaultTrailPct) { this.defaultTrailPct = defaultTrailPct; }

        public double getActivationPct() { return activationPct; }
        public void setActivationPct(double activationPct) { this.activationPct = activationPct; }

        public double getStepPct() { return stepPct; }
        public void setStepPct(double stepPct) { this.stepPct = stepPct; }
    }

    public static class BreakEven {
        private boolean enabled = true;
        private double activationPct = 1.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getActivationPct() { return activationPct; }
        public void setActivationPct(double activationPct) { this.activationPct = activationPct; }
    }
}
