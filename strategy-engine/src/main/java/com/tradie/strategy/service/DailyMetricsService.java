package com.tradie.strategy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DailyMetricsService {

    private static final Logger log = LoggerFactory.getLogger(DailyMetricsService.class);

    private static final ZoneId EST = ZoneId.of("America/New_York");
    private static final String KEY_PREFIX = "metrics:daily:";
    private static final String FIELD_DAILY_PNL = "daily_pnl";
    private static final String FIELD_DAILY_TRADES = "daily_trades";
    private static final String FIELD_WIN_COUNT = "win_count";
    private static final String FIELD_LOSS_COUNT = "loss_count";
    private static final String FIELD_CONSECUTIVE_LOSSES = "consecutive_losses";

    private final StringRedisTemplate redisTemplate;

    public DailyMetricsService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public BigDecimal getDailyPnl() {
        String value = (String) redisTemplate.opsForHash().get(todayKey(), FIELD_DAILY_PNL);
        return value != null ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    public long getConsecutiveLosses() {
        String value = (String) redisTemplate.opsForHash().get(todayKey(), FIELD_CONSECUTIVE_LOSSES);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public long getDailyTradeCount() {
        String value = (String) redisTemplate.opsForHash().get(todayKey(), FIELD_DAILY_TRADES);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public void incrementTradeCount() {
        String key = todayKey();
        redisTemplate.opsForHash().increment(key, FIELD_DAILY_TRADES, 1);
        ensureTtl(key);
    }

    public void recordWin(BigDecimal pnl) {
        String key = todayKey();
        redisTemplate.opsForHash().put(key, FIELD_CONSECUTIVE_LOSSES, "0");
        redisTemplate.opsForHash().increment(key, FIELD_WIN_COUNT, 1);
        adjustDailyPnl(key, pnl);
        ensureTtl(key);
        log.debug("Recorded win, pnl={}", pnl);
    }

    public void recordLoss(BigDecimal pnl) {
        String key = todayKey();
        redisTemplate.opsForHash().increment(key, FIELD_LOSS_COUNT, 1);
        redisTemplate.opsForHash().increment(key, FIELD_CONSECUTIVE_LOSSES, 1);
        adjustDailyPnl(key, pnl);
        ensureTtl(key);
        log.debug("Recorded loss, pnl={}", pnl);
    }

    private void adjustDailyPnl(String key, BigDecimal delta) {
        redisTemplate.opsForHash().increment(key, FIELD_DAILY_PNL, delta.doubleValue());
    }

    private void ensureTtl(String key) {
        redisTemplate.expire(key, Duration.ofHours(48));
    }

    // Protected to allow date injection in unit tests
    protected LocalDate today() {
        return LocalDate.now(EST);
    }

    private String todayKey() {
        return KEY_PREFIX + today();
    }
}
