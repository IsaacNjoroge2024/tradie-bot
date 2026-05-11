package com.tradie.strategy.service;

import com.tradie.strategy.dto.AccountInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountService {

    @Value("${tradie.risk.default-account-balance:10000.0}")
    private double defaultAccountBalance;

    /**
     * Returns configuration-based account info.
     * Phase 2: config-driven. Phase 3+: will integrate with IBKR for real-time data.
     */
    @Cacheable("accountInfo")
    public AccountInfo getAccountInfo() {
        BigDecimal value = BigDecimal.valueOf(defaultAccountBalance);
        return new AccountInfo(value, value, value, BigDecimal.ZERO);
    }
}
