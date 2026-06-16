package com.tradie.strategy.service;

import com.tradie.strategy.dto.AccountInfo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountService {

    @Value("${tradie.risk.default-account-balance:10000.0}")
    private BigDecimal defaultAccountBalance;

    private final MeterRegistry meterRegistry;

    public AccountService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("tradie.account.balance", this, svc -> svc.defaultAccountBalance.doubleValue())
                .description("Configured account balance used for risk calculations")
                .register(meterRegistry);
    }

    /**
     * Returns configuration-based account info.
     * Phase 2: config-driven. Phase 3+: will integrate with IBKR for real-time data.
     */
    @Cacheable("accountInfo")
    public AccountInfo getAccountInfo() {
        return new AccountInfo(defaultAccountBalance, defaultAccountBalance,
                defaultAccountBalance, BigDecimal.ZERO);
    }
}
