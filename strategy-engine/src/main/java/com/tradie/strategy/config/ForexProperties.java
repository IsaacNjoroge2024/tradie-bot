package com.tradie.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie.forex")
public class ForexProperties {

    private String defaultLotType = "MICRO";
    private double minLotSize = 0.01;
    private double maxLotSize = 10.0;
    private int leverage = 50;
    private Sessions sessions = new Sessions();
    private Risk risk = new Risk();

    public String getDefaultLotType() { return defaultLotType; }
    public void setDefaultLotType(String defaultLotType) { this.defaultLotType = defaultLotType; }

    public double getMinLotSize() { return minLotSize; }
    public void setMinLotSize(double minLotSize) {
        if (minLotSize <= 0) throw new IllegalArgumentException("minLotSize must be > 0");
        this.minLotSize = minLotSize;
    }

    public double getMaxLotSize() { return maxLotSize; }
    public void setMaxLotSize(double maxLotSize) {
        if (maxLotSize <= 0) throw new IllegalArgumentException("maxLotSize must be > 0");
        this.maxLotSize = maxLotSize;
    }

    public int getLeverage() { return leverage; }
    public void setLeverage(int leverage) {
        if (leverage <= 0) throw new IllegalArgumentException("leverage must be > 0");
        this.leverage = leverage;
    }

    public Sessions getSessions() { return sessions; }
    public void setSessions(Sessions sessions) { this.sessions = sessions; }

    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }

    public static class Sessions {
        private boolean preferOverlap = true;
        private boolean warnLowLiquidity = true;

        public boolean isPreferOverlap() { return preferOverlap; }
        public void setPreferOverlap(boolean preferOverlap) { this.preferOverlap = preferOverlap; }

        public boolean isWarnLowLiquidity() { return warnLowLiquidity; }
        public void setWarnLowLiquidity(boolean warnLowLiquidity) {
            this.warnLowLiquidity = warnLowLiquidity;
        }
    }

    public static class Risk {
        private double maxSpreadPips = 5.0;
        private boolean weekendWarning = true;

        public double getMaxSpreadPips() { return maxSpreadPips; }
        public void setMaxSpreadPips(double maxSpreadPips) { this.maxSpreadPips = maxSpreadPips; }

        public boolean isWeekendWarning() { return weekendWarning; }
        public void setWeekendWarning(boolean weekendWarning) { this.weekendWarning = weekendWarning; }
    }
}
