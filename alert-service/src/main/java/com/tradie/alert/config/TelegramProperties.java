package com.tradie.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tradie.telegram")
public class TelegramProperties {

    private String botToken = "";
    private String chatId = "";
    private boolean enabled = true;

    private Alerts alerts = new Alerts();
    private Throttling throttling = new Throttling();
    private Summary summary = new Summary();

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Alerts getAlerts() { return alerts; }
    public void setAlerts(Alerts alerts) { this.alerts = alerts; }

    public Throttling getThrottling() { return throttling; }
    public void setThrottling(Throttling throttling) { this.throttling = throttling; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public static class Alerts {
        private boolean tradeEntry = true;
        private boolean tradeExit = true;
        private boolean signalRejected = true;
        private boolean systemAlerts = true;

        public boolean isTradeEntry() { return tradeEntry; }
        public void setTradeEntry(boolean tradeEntry) { this.tradeEntry = tradeEntry; }

        public boolean isTradeExit() { return tradeExit; }
        public void setTradeExit(boolean tradeExit) { this.tradeExit = tradeExit; }

        public boolean isSignalRejected() { return signalRejected; }
        public void setSignalRejected(boolean signalRejected) { this.signalRejected = signalRejected; }

        public boolean isSystemAlerts() { return systemAlerts; }
        public void setSystemAlerts(boolean systemAlerts) { this.systemAlerts = systemAlerts; }
    }

    public static class Throttling {
        private int maxPerMinute = 10;
        private boolean aggregateSimilar = true;

        public int getMaxPerMinute() { return maxPerMinute; }
        public void setMaxPerMinute(int maxPerMinute) { this.maxPerMinute = maxPerMinute; }

        public boolean isAggregateSimilar() { return aggregateSimilar; }
        public void setAggregateSimilar(boolean aggregateSimilar) { this.aggregateSimilar = aggregateSimilar; }
    }

    public static class Summary {
        private boolean enabled = true;
        private String timezone = "America/New_York";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
    }
}
