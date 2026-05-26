package com.tradie.executor.controller;

import com.tradie.executor.dto.AccountData;
import com.tradie.executor.ibkr.IbkrAccountService;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.OrderIdManager;
import com.tradie.executor.ibkr.PositionTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IbkrStatusController.class)
class IbkrStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IBConnectionManager connectionManager;

    @MockBean
    private IbkrAccountService accountService;

    @MockBean
    private OrderIdManager orderIdManager;

    @MockBean
    private PositionTracker positionTracker;

    @Test
    void getStatus_connected_returnsFullStatus() throws Exception {
        Instant now = Instant.parse("2025-01-15T10:30:00Z");
        Instant heartbeat = Instant.parse("2025-01-15T10:35:30Z");
        AccountData account = new AccountData("DU123456", 55000.0, 30000.0, 50000.0, 45000.0, now);

        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getConnectionTime()).thenReturn(now);
        when(connectionManager.getLastHeartbeat()).thenReturn(heartbeat);
        when(accountService.getAccountData()).thenReturn(account);
        when(orderIdManager.peekNextOrderId()).thenReturn(1234);
        when(positionTracker.getOpenPositionCount()).thenReturn(3);

        mockMvc.perform(get("/api/ibkr/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectionTime").value("2025-01-15T10:30:00Z"))
                .andExpect(jsonPath("$.lastHeartbeat").value("2025-01-15T10:35:30Z"))
                .andExpect(jsonPath("$.accountId").value("DU123456"))
                .andExpect(jsonPath("$.buyingPower").value(50000.0))
                .andExpect(jsonPath("$.nextOrderId").value(1234))
                .andExpect(jsonPath("$.openPositions").value(3));
    }

    @Test
    void getStatus_disconnected_returnsDisconnectedStatus() throws Exception {
        when(connectionManager.isConnected()).thenReturn(false);
        when(connectionManager.getConnectionTime()).thenReturn(null);
        when(connectionManager.getLastHeartbeat()).thenReturn(null);
        when(accountService.getAccountData()).thenReturn(null);
        when(orderIdManager.peekNextOrderId()).thenReturn(0);
        when(positionTracker.getOpenPositionCount()).thenReturn(0);

        mockMvc.perform(get("/api/ibkr/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.connectionTime").doesNotExist())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.buyingPower").value(0.0))
                .andExpect(jsonPath("$.nextOrderId").value(0))
                .andExpect(jsonPath("$.openPositions").value(0));
    }

    @Test
    void getStatus_connectedNoAccountData_returnsBuyingPowerZero() throws Exception {
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.getConnectionTime()).thenReturn(Instant.now());
        when(connectionManager.getLastHeartbeat()).thenReturn(Instant.now());
        when(accountService.getAccountData()).thenReturn(null);
        when(orderIdManager.peekNextOrderId()).thenReturn(100);
        when(positionTracker.getOpenPositionCount()).thenReturn(0);

        mockMvc.perform(get("/api/ibkr/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.buyingPower").value(0.0));
    }
}
