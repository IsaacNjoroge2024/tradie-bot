package com.tradie.strategy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the trading pause state from Redis. The pause state is written by the Alert Service
 * when a /pause command is received via Telegram.
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
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PAUSED_KEY));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable when checking pause state; treating as not paused: {}", e.getMessage());
            return false;
        }
    }

    public String getPauseReason() {
        try {
            return redisTemplate.opsForValue().get(PAUSED_KEY);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable when fetching pause reason: {}", e.getMessage());
            return null;
        }
    }
}
