package com.tradie.strategy.crypto;

import com.tradie.strategy.config.CryptoProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Determines trading eligibility for crypto assets.
 *
 * <p>Crypto markets on IBKR Paxos are open 24/7 with no weekend closure.
 * Optionally skips low-volume hours (00:00–06:00 UTC) when configured.
 */
@Component
public class CryptoMarketHours {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final int LOW_VOLUME_START_HOUR = 0;
    private static final int LOW_VOLUME_END_HOUR = 6;

    private final CryptoProperties cryptoProperties;

    public CryptoMarketHours(CryptoProperties cryptoProperties) {
        this.cryptoProperties = cryptoProperties;
    }

    /**
     * Returns {@code true} — crypto markets are always open.
     */
    public boolean isMarketOpen() {
        return true;
    }

    /**
     * Returns {@code true} if trading is appropriate at the given instant.
     *
     * <p>When {@code tradie.crypto.skip-low-volume-hours=true}, signals arriving
     * between 00:00 and 06:00 UTC are skipped to avoid low-liquidity conditions.
     */
    public boolean shouldTrade(Instant time) {
        if (!cryptoProperties.isSkipLowVolumeHours()) {
            return true;
        }
        int hour = time.atZone(UTC).getHour();
        return hour < LOW_VOLUME_START_HOUR || hour >= LOW_VOLUME_END_HOUR;
    }
}
