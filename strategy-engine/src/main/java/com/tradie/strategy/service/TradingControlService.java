package com.tradie.strategy.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the trading pause state from Redis. The pause state is written by the Alert Service
 * when a /pause command is received via Telegram.
 */
@Service
public class TradingControlService {

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
}
