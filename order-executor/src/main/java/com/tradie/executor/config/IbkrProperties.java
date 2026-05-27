package com.tradie.executor.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tradie.ibkr")
public class IbkrProperties {

    @NotBlank
    private String host = "127.0.0.1";
    @Min(1)
    private int port = 7497;
    @Min(0)
    private int clientId = 1;
    @Min(1)
    private int connectionTimeoutSeconds = 30;
    @Min(1)
    private int heartbeatIntervalSeconds = 30;
    @Min(1)
    private int heartbeatTimeoutSeconds = 90;
    @Min(1)
    private int maxReconnectAttempts = 10;
    @Min(1)
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
