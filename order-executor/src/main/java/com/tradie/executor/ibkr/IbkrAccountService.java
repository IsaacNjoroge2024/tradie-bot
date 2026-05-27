package com.tradie.executor.ibkr;

import com.tradie.executor.dto.AccountData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages IBKR account data retrieved via reqAccountSummary callbacks.
 * Stores the latest account snapshot and schedules periodic refresh requests.
 */
@Service
public class IbkrAccountService {

    private static final Logger log = LoggerFactory.getLogger(IbkrAccountService.class);
    private static final int ACCOUNT_SUMMARY_REQ_ID = 9001;
    private static final String ACCOUNT_TAGS =
            "NetLiquidation,TotalCashValue,BuyingPower,AvailableFunds";

    private final AtomicReference<AccountData> accountData = new AtomicReference<>();
    private volatile String accountId;
    private volatile double netLiquidation;
    private volatile double totalCashValue;
    private volatile double buyingPower;
    private volatile double availableFunds;

    // Injected lazily to avoid circular dependency
    private IBConnectionManager connectionManager;

    void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Called by TradieEWrapper when the managedAccounts callback fires.
     * Captures the primary account ID (first account if multiple).
     */
    public void onManagedAccounts(String accountsList) {
        if (accountsList != null && !accountsList.isBlank()) {
            this.accountId = accountsList.split(",")[0].trim();
            log.info("Managed account received from IBKR");
        }
    }

    /**
     * Called by TradieEWrapper for each accountSummary tag received.
     */
    public void onAccountSummary(String tag, String value) {
        try {
            switch (tag) {
                case "NetLiquidation"  -> netLiquidation = Double.parseDouble(value);
                case "TotalCashValue"  -> totalCashValue = Double.parseDouble(value);
                case "BuyingPower"     -> buyingPower = Double.parseDouble(value);
                case "AvailableFunds"  -> availableFunds = Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse account summary tag={} value={}: {}", tag, value, e.getMessage());
        }
    }

    /**
     * Called by TradieEWrapper when accountSummaryEnd fires.
     * Commits the in-progress values as the new AccountData snapshot.
     */
    public void onAccountSummaryEnd() {
        accountData.set(new AccountData(
                accountId,
                netLiquidation,
                totalCashValue,
                buyingPower,
                availableFunds,
                Instant.now()
        ));
        log.debug("Account data snapshot updated");
    }

    /**
     * Returns the latest account data snapshot, or null if not yet received.
     */
    public AccountData getAccountData() {
        return accountData.get();
    }

    /**
     * Scheduled refresh: re-requests account summary from IBKR every 60 seconds.
     * Only fires if connected.
     */
    @Scheduled(fixedRateString = "${tradie.ibkr.account-refresh-seconds:60}000")
    public void refreshAccountData() {
        if (connectionManager != null && connectionManager.isConnected()) {
            connectionManager.requestAccountSummary(ACCOUNT_SUMMARY_REQ_ID, ACCOUNT_TAGS);
        }
    }

    public static int getAccountSummaryReqId() {
        return ACCOUNT_SUMMARY_REQ_ID;
    }

    public static String getAccountTags() {
        return ACCOUNT_TAGS;
    }
}
