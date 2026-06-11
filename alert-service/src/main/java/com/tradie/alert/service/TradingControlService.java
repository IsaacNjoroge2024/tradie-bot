package com.tradie.alert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Controls the trading pause state stored in Redis.
 * The Strategy Engine reads this key before processing each signal.
 * Key: {@code trading:paused} — present means paused, absent means active.
 */
@Service
public class TradingControlService {

    private static final Logger log = LoggerFactory.getLogger(TradingControlService.class);
    static final String PAUSED_KEY = "trading:paused";

    private final StringRedisTemplate redisTemplate;

    public TradingControlService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isPaused() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PAUSED_KEY));
    }

    public String getPauseReason() {
        return redisTemplate.opsForValue().get(PAUSED_KEY);
    }

    public void pause(String reason) {
        String value = (reason != null && !reason.isBlank()) ? reason : "manual pause";
        redisTemplate.opsForValue().set(PAUSED_KEY, value);
        log.info("Trading PAUSED: {}", value);
    }

    public void resume() {
        redisTemplate.delete(PAUSED_KEY);
        log.info("Trading RESUMED");
    }
}
