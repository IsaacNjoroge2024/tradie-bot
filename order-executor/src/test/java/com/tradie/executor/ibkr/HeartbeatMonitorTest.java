package com.tradie.executor.ibkr;

import com.tradie.executor.config.IbkrProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private IBConnectionManager connectionManager;

    private IbkrProperties properties;
    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        properties = new IbkrProperties();
        properties.setHeartbeatTimeoutSeconds(90);
        heartbeatMonitor = new HeartbeatMonitor(connectionManager, properties);
    }

    @Test
    void checkConnection_notConnected_doesNothing() {
        when(connectionManager.isConnected()).thenReturn(false);

        heartbeatMonitor.checkConnection();

        verify(connectionManager, never()).disconnect();
        verify(connectionManager, never()).connect();
    }

    @Test
    void checkConnection_connectedRecentHeartbeat_doesNotReconnect() {
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getLastHeartbeat()).thenReturn(Instant.now().minusSeconds(30));

        heartbeatMonitor.checkConnection();

        verify(connectionManager, never()).disconnect();
        verify(connectionManager, never()).connect();
    }

    @Test
    void checkConnection_heartbeatTimeout_triggersReconnect() {
        when(connectionManager.isConnected()).thenReturn(true);
        // Last heartbeat was 100s ago (beyond the 90s timeout)
        when(connectionManager.getLastHeartbeat()).thenReturn(Instant.now().minusSeconds(100));

        heartbeatMonitor.checkConnection();

        verify(connectionManager).disconnect();
        verify(connectionManager).connect();
    }

    @Test
    void checkConnection_nullLastHeartbeat_doesNotReconnect() {
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getLastHeartbeat()).thenReturn(null);

        heartbeatMonitor.checkConnection();

        verify(connectionManager, never()).disconnect();
        verify(connectionManager, never()).connect();
    }

    @Test
    void checkConnection_exactlyAtTimeout_doesNotReconnect() {
        when(connectionManager.isConnected()).thenReturn(true);
        // Exactly at the timeout boundary — should NOT reconnect (strictly greater than)
        when(connectionManager.getLastHeartbeat()).thenReturn(Instant.now().minusSeconds(90));

        heartbeatMonitor.checkConnection();

        verify(connectionManager, never()).disconnect();
        verify(connectionManager, never()).connect();
    }
}
