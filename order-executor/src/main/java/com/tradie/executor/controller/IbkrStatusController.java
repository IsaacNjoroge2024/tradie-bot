package com.tradie.executor.controller;

import com.tradie.executor.dto.AccountData;
import com.tradie.executor.dto.IbkrStatusResponse;
import com.tradie.executor.ibkr.IbkrAccountService;
import com.tradie.executor.ibkr.IBConnectionManager;
import com.tradie.executor.ibkr.OrderIdManager;
import com.tradie.executor.ibkr.PositionTracker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint exposing IBKR connection health and account status.
 * Used by monitoring systems and the Telegram Alert Service.
 *
 * GET /api/ibkr/status
 */
@RestController
@RequestMapping("/api/ibkr")
public class IbkrStatusController {

    private final IBConnectionManager connectionManager;
    private final IbkrAccountService accountService;
    private final OrderIdManager orderIdManager;
    private final PositionTracker positionTracker;

    public IbkrStatusController(
            IBConnectionManager connectionManager,
            IbkrAccountService accountService,
            OrderIdManager orderIdManager,
            PositionTracker positionTracker) {
        this.connectionManager = connectionManager;
        this.accountService = accountService;
        this.orderIdManager = orderIdManager;
        this.positionTracker = positionTracker;
    }

    @GetMapping("/status")
    public IbkrStatusResponse getStatus() {
        AccountData account = accountService.getAccountData();
        return new IbkrStatusResponse(
                connectionManager.isConnected(),
                connectionManager.getConnectionTime(),
                connectionManager.getLastHeartbeat(),
                account != null ? account.accountId() : null,
                account != null ? account.buyingPower() : 0.0,
                orderIdManager.peekNextOrderId(),
                positionTracker.getOpenPositionCount()
        );
    }
}
