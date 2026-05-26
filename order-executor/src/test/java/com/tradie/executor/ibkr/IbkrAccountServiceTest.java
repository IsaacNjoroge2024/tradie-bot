package com.tradie.executor.ibkr;

import com.tradie.executor.dto.AccountData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbkrAccountServiceTest {

    private IbkrAccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new IbkrAccountService();
    }

    @Test
    void initialState_accountDataIsNull() {
        assertThat(accountService.getAccountData()).isNull();
    }

    @Test
    void onManagedAccounts_setsAccountId() {
        accountService.onManagedAccounts("DU123456");
        accountService.onAccountSummaryEnd();

        AccountData data = accountService.getAccountData();
        assertThat(data).isNotNull();
        assertThat(data.accountId()).isEqualTo("DU123456");
    }

    @Test
    void onManagedAccounts_multipleAccounts_usesFirstOne() {
        accountService.onManagedAccounts("DU123456,DU789012");
        accountService.onAccountSummaryEnd();

        assertThat(accountService.getAccountData().accountId()).isEqualTo("DU123456");
    }

    @Test
    void onAccountSummary_updatesEachTag() {
        accountService.onAccountSummary("NetLiquidation", "50000.0");
        accountService.onAccountSummary("TotalCashValue", "30000.0");
        accountService.onAccountSummary("BuyingPower", "100000.0");
        accountService.onAccountSummary("AvailableFunds", "45000.0");
        accountService.onAccountSummaryEnd();

        AccountData data = accountService.getAccountData();
        assertThat(data.netLiquidation()).isEqualTo(50000.0);
        assertThat(data.totalCashValue()).isEqualTo(30000.0);
        assertThat(data.buyingPower()).isEqualTo(100000.0);
        assertThat(data.availableFunds()).isEqualTo(45000.0);
    }

    @Test
    void onAccountSummary_invalidValue_doesNotThrow() {
        // Should log a warning and skip the bad value without crashing
        accountService.onAccountSummary("BuyingPower", "N/A");
        accountService.onAccountSummaryEnd();

        // BuyingPower defaults to 0.0 since the parse failed
        assertThat(accountService.getAccountData().buyingPower()).isEqualTo(0.0);
    }

    @Test
    void onAccountSummaryEnd_setsUpdatedAt() {
        accountService.onAccountSummaryEnd();

        AccountData data = accountService.getAccountData();
        assertThat(data).isNotNull();
        assertThat(data.updatedAt()).isNotNull();
    }

    @Test
    void onAccountSummaryEnd_calledMultipleTimes_updatesSnapshot() {
        accountService.onAccountSummary("BuyingPower", "10000.0");
        accountService.onAccountSummaryEnd();

        accountService.onAccountSummary("BuyingPower", "20000.0");
        accountService.onAccountSummaryEnd();

        assertThat(accountService.getAccountData().buyingPower()).isEqualTo(20000.0);
    }

    @Test
    void onManagedAccounts_blankInput_doesNotSetAccountId() {
        accountService.onManagedAccounts("   ");
        accountService.onAccountSummaryEnd();

        // accountId stays null since blank input was ignored
        assertThat(accountService.getAccountData().accountId()).isNull();
    }
}
