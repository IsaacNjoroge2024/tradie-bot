package com.tradie.strategy.service;

import com.tradie.strategy.dto.AccountInfo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceTest {

    private SimpleMeterRegistry registry;
    private AccountService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new AccountService(registry);
        ReflectionTestUtils.setField(service, "defaultAccountBalance", new BigDecimal("10000.0"));
        service.registerMetrics();
    }

    @Test
    void getAccountInfo_returnsConfiguredBalance() {
        AccountInfo info = service.getAccountInfo();

        assertThat(info.accountValue()).isEqualByComparingTo(new BigDecimal("10000.0"));
    }

    @Test
    void registerMetrics_exposesAccountBalanceGauge() {
        Gauge gauge = registry.find("tradie.account.balance").gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(10000.0);
    }
}
