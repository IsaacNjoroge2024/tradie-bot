package com.tradie.executor.ibkr;

import com.tradie.executor.config.IbkrProperties;
import com.tradie.executor.dto.AccountData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the IBKR TWS connection.
 *
 * DISABLED by default because it requires:
 * 1. TWS or IB Gateway running on localhost:7497 (paper trading port)
 * 2. API connections enabled in TWS: Configure → API → Settings → Enable ActiveX and Socket Clients
 * 3. A valid paper trading account
 *
 * To run manually:
 *   1. Start TWS and log in to your paper trading account
 *   2. Enable API: Configure → API → Settings → tick "Enable ActiveX and Socket Clients"
 *   3. Remove the @Disabled annotation and run this test
 */
@Disabled("Requires TWS/IB Gateway running on localhost:7497")
class IBConnectionIntegrationTest {

    private IbkrProperties properties;
    private IbkrAccountService accountService;
    private PositionTracker positionTracker;
    private OrderIdManager orderIdManager;
    private IBConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        properties = new IbkrProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(7497);
        properties.setClientId(99); // Use a high clientId to avoid conflicts
        properties.setConnectionTimeoutSeconds(30);
        properties.setHeartbeatTimeoutSeconds(90);
        properties.setMaxReconnectAttempts(3);
        properties.setAccountRefreshSeconds(60);

        accountService = new IbkrAccountService();
        positionTracker = new PositionTracker();
        orderIdManager = new OrderIdManager();
        connectionManager = new IBConnectionManager(
                properties, accountService, positionTracker, orderIdManager);
    }

    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
    }

    @Test
    void connectsToTWS_receivesNextValidId() throws InterruptedException {
        connectionManager.init();

        waitUntil(connectionManager::isConnected, 15);

        assertThat(connectionManager.isConnected()).isTrue();
        assertThat(orderIdManager.peekNextOrderId()).isGreaterThan(0);
    }

    @Test
    void retrievesAccountData_afterConnection() throws InterruptedException {
        connectionManager.init();
        waitUntil(connectionManager::isConnected, 15);

        assertThat(connectionManager.isConnected()).isTrue();

        waitUntil(() -> accountService.getAccountData() != null, 10);

        AccountData account = accountService.getAccountData();
        assertThat(account).isNotNull();
        assertThat(account.accountId()).isNotBlank();
        assertThat(account.buyingPower()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void tracksPositions_afterConnection() throws InterruptedException {
        connectionManager.init();
        waitUntil(connectionManager::isConnected, 15);

        assertThat(connectionManager.isConnected()).isTrue();

        // Position count can be 0 on a paper account with no open positions — that is valid
        assertThat(positionTracker.getOpenPositionCount()).isGreaterThanOrEqualTo(0);
    }

    private static void waitUntil(BooleanSupplier condition, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Condition not met within " + timeoutSeconds + "s timeout");
    }
}
