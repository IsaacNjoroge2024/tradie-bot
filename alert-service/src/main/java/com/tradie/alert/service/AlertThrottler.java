package com.tradie.alert.service;

import com.tradie.alert.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throttles Telegram alerts to prevent spam.
 * Counts alerts per {@code type:symbol} key and rejects those exceeding {@code maxPerMinute}.
 * Counts are reset every 60 seconds via a scheduled task.
 */
@ConditionalOnProperty(name = "tradie.telegram.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class AlertThrottler {

    private static final Logger log = LoggerFactory.getLogger(AlertThrottler.class);

    private final ConcurrentHashMap<String, AtomicInteger> alertCounts = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    public AlertThrottler(TelegramProperties properties) {
        this.maxPerMinute = properties.getThrottling().getMaxPerMinute();
    }

    /**
     * Returns true if the alert should be sent, false if it has been throttled.
     *
     * @param key a composite key identifying the alert type and symbol, e.g. {@code FILLED:AAPL}
     */
    public boolean shouldSend(String key) {
        AtomicInteger count = alertCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();
        if (current > maxPerMinute) {
            log.debug("Alert throttled: key={} count={} max={}", key, current, maxPerMinute);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 60_000)
    void resetCounts() {
        alertCounts.clear();
        log.debug("Alert throttle counts reset");
    }
}
