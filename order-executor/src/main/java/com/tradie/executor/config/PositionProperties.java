package com.tradie.executor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tradie")
public class PositionProperties {

    @Valid
    private Positions positions = new Positions();
    @Valid
    private TrailingStop trailingStop = new TrailingStop();
    @Valid
    private BreakEven breakEven = new BreakEven();

    public Positions getPositions() { return positions; }
    public void setPositions(Positions positions) { this.positions = positions; }

    public TrailingStop getTrailingStop() { return trailingStop; }
    public void setTrailingStop(TrailingStop trailingStop) { this.trailingStop = trailingStop; }

    public BreakEven getBreakEven() { return breakEven; }
    public void setBreakEven(BreakEven breakEven) { this.breakEven = breakEven; }

    public static class Positions {
        @Min(1000)
        private int syncIntervalMs = 300000;
        @Min(100)
        private int priceUpdateIntervalMs = 1000;

        public int getSyncIntervalMs() { return syncIntervalMs; }
        public void setSyncIntervalMs(int syncIntervalMs) { this.syncIntervalMs = syncIntervalMs; }

        public int getPriceUpdateIntervalMs() { return priceUpdateIntervalMs; }
        public void setPriceUpdateIntervalMs(int priceUpdateIntervalMs) { this.priceUpdateIntervalMs = priceUpdateIntervalMs; }
    }

    public static class TrailingStop {
        private boolean enabled = true;
        @DecimalMin("0.01") @DecimalMax("50.0")
        private double defaultTrailPct = 2.0;
        @DecimalMin("0.01") @DecimalMax("50.0")
        private double activationPct = 1.0;
        @DecimalMin("0.01") @DecimalMax("10.0")
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
        @DecimalMin("0.01") @DecimalMax("50.0")
        private double activationPct = 1.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getActivationPct() { return activationPct; }
        public void setActivationPct(double activationPct) { this.activationPct = activationPct; }
    }
}
