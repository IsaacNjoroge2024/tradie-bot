package com.tradie.alert.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingControlServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    private TradingControlService service;

    @BeforeEach
    void setUp() {
        service = new TradingControlService(redisTemplate);
    }

    @Test
    void isPaused_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(TradingControlService.PAUSED_KEY)).thenReturn(false);
        assertFalse(service.isPaused());
    }

    @Test
    void isPaused_keyPresent_returnsTrue() {
        when(redisTemplate.hasKey(TradingControlService.PAUSED_KEY)).thenReturn(true);
        assertTrue(service.isPaused());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pause_setsRedisKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service.pause("news event");
        verify(valueOps).set(TradingControlService.PAUSED_KEY, "news event");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pause_nullReason_usesDefaultReason() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service.pause(null);
        verify(valueOps).set(TradingControlService.PAUSED_KEY, "manual pause");
    }

    @Test
    void resume_deletesRedisKey() {
        service.resume();
        verify(redisTemplate).delete(TradingControlService.PAUSED_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPauseReason_returnsStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(TradingControlService.PAUSED_KEY)).thenReturn("manual");
        assertEquals("manual", service.getPauseReason());
    }
}
