package com.tradie.executor.ibkr;

import com.tradie.executor.config.IbkrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Monitors IBKR connection health by tracking the last-message timestamp.
 *
 * IBKR sends heartbeat messages approximately every 60 seconds. If no message
 * is received within heartbeat-timeout-seconds (default 90s), the connection
 * is assumed dead and a reconnect is triggered.
 *
 * The check runs every heartbeat-interval-seconds (default 30s).
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final IBConnectionManager connectionManager;
    private final IbkrProperties properties;

    public HeartbeatMonitor(IBConnectionManager connectionManager, IbkrProperties properties) {
        this.connectionManager = connectionManager;
        this.properties = properties;
    }

    @Scheduled(fixedRateString = "${tradie.ibkr.heartbeat-interval-seconds:30}000")
    public void checkConnection() {
        if (!connectionManager.isConnected()) {
            return;
        }

        Instant lastHeartbeat = connectionManager.getLastHeartbeat();
        if (lastHeartbeat == null) {
            return;
        }

        long secondsSinceLastMessage = Instant.now().getEpochSecond() - lastHeartbeat.getEpochSecond();
        if (secondsSinceLastMessage > properties.getHeartbeatTimeoutSeconds()) {
            log.warn("No IBKR heartbeat received for {}s (timeout={}s). Triggering reconnect.",
                    secondsSinceLastMessage, properties.getHeartbeatTimeoutSeconds());
            connectionManager.disconnect();
            connectionManager.connect();
        } else {
            log.debug("Heartbeat OK. Last message {}s ago.", secondsSinceLastMessage);
        }
    }
}
