package com.tradie.executor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tradie.ibkr")
public class IbkrProperties {

    private String host = "127.0.0.1";
    private int port = 7497;
    private int clientId = 1;
    private int connectionTimeoutSeconds = 30;
    private int heartbeatIntervalSeconds = 30;
    private int heartbeatTimeoutSeconds = 90;
    private int maxReconnectAttempts = 10;
    private int accountRefreshSeconds = 60;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }

    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
    public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    public int getAccountRefreshSeconds() { return accountRefreshSeconds; }
    public void setAccountRefreshSeconds(int accountRefreshSeconds) {
        this.accountRefreshSeconds = accountRefreshSeconds;
    }
}
