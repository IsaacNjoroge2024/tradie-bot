package com.tradie.strategy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyMetricsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private DailyMetricsService service;
    private static final String FIXED_DATE = "2026-05-04";
    private static final String EXPECTED_KEY = "metrics:daily:" + FIXED_DATE;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new DailyMetricsService(redisTemplate) {
            @Override
            protected LocalDate today() {
                return LocalDate.parse(FIXED_DATE);
            }
        };
    }

    @Test
    void getDailyPnl_noValueInRedis_returnsZero() {
        when(hashOps.get(EXPECTED_KEY, "daily_pnl")).thenReturn(null);

        BigDecimal pnl = service.getDailyPnl();

        assertEquals(BigDecimal.ZERO, pnl);
    }

    @Test
    void getDailyPnl_valueInRedis_returnsParsedValue() {
        when(hashOps.get(EXPECTED_KEY, "daily_pnl")).thenReturn("-250.50");

        BigDecimal pnl = service.getDailyPnl();

        assertEquals(new BigDecimal("-250.50"), pnl);
    }

    @Test
    void getConsecutiveLosses_noValue_returnsZero() {
        when(hashOps.get(EXPECTED_KEY, "consecutive_losses")).thenReturn(null);

        assertEquals(0L, service.getConsecutiveLosses());
    }

    @Test
    void getConsecutiveLosses_valueInRedis_returnsParsed() {
        when(hashOps.get(EXPECTED_KEY, "consecutive_losses")).thenReturn("3");

        assertEquals(3L, service.getConsecutiveLosses());
    }

    @Test
    void recordWin_resetsConsecutiveLossesAndIncrementsWinCount() {
        when(hashOps.get(EXPECTED_KEY, "daily_pnl")).thenReturn("100.00");

        service.recordWin(BigDecimal.valueOf(50));

        verify(hashOps).put(EXPECTED_KEY, "consecutive_losses", "0");
        verify(hashOps).increment(EXPECTED_KEY, "win_count", 1);
        verify(hashOps).put(eq(EXPECTED_KEY), eq("daily_pnl"), anyString());
        verify(redisTemplate).expire(eq(EXPECTED_KEY), any());
    }

    @Test
    void recordLoss_incrementsLossCountAndConsecutiveLosses() {
        when(hashOps.get(EXPECTED_KEY, "daily_pnl")).thenReturn("0");

        service.recordLoss(BigDecimal.valueOf(-100));

        verify(hashOps).increment(EXPECTED_KEY, "loss_count", 1);
        verify(hashOps).increment(EXPECTED_KEY, "consecutive_losses", 1);
        verify(redisTemplate).expire(eq(EXPECTED_KEY), any());
    }

    @Test
    void incrementTradeCount_callsRedisIncrement() {
        service.incrementTradeCount();

        verify(hashOps).increment(EXPECTED_KEY, "daily_trades", 1);
        verify(redisTemplate).expire(eq(EXPECTED_KEY), any());
    }
}
