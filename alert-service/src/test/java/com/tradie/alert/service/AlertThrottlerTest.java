package com.tradie.alert.service;

import com.tradie.alert.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertThrottlerTest {

    private AlertThrottler throttler;

    @BeforeEach
    void setUp() {
        TelegramProperties props = new TelegramProperties();
        props.getThrottling().setMaxPerMinute(3);
        throttler = new AlertThrottler(props);
    }

    @Test
    void shouldSend_underLimit_returnsTrue() {
        assertTrue(throttler.shouldSend("FILLED:AAPL"));
        assertTrue(throttler.shouldSend("FILLED:AAPL"));
        assertTrue(throttler.shouldSend("FILLED:AAPL"));
    }

    @Test
    void shouldSend_overLimit_returnsFalse() {
        throttler.shouldSend("FILLED:AAPL");
        throttler.shouldSend("FILLED:AAPL");
        throttler.shouldSend("FILLED:AAPL");

        assertFalse(throttler.shouldSend("FILLED:AAPL"));
    }

    @Test
    void shouldSend_differentKeys_trackedSeparately() {
        for (int i = 0; i < 3; i++) {
            throttler.shouldSend("FILLED:AAPL");
        }
        // TSLA has a fresh counter
        assertTrue(throttler.shouldSend("FILLED:TSLA"));
    }

    @Test
    void shouldSend_maxPerMinuteZero_defaultsToOneAndAllowsFirstSend() {
        TelegramProperties props = new TelegramProperties();
        props.getThrottling().setMaxPerMinute(0);
        AlertThrottler zeroThrottler = new AlertThrottler(props);

        assertTrue(zeroThrottler.shouldSend("FILLED:AAPL"));
        assertFalse(zeroThrottler.shouldSend("FILLED:AAPL"));
    }

    @Test
    void shouldSend_maxPerMinuteNegative_defaultsToOneAndAllowsFirstSend() {
        TelegramProperties props = new TelegramProperties();
        props.getThrottling().setMaxPerMinute(-1);
        AlertThrottler negThrottler = new AlertThrottler(props);

        assertTrue(negThrottler.shouldSend("FILLED:AAPL"));
        assertFalse(negThrottler.shouldSend("FILLED:AAPL"));
    }

    @Test
    void resetCounts_allowsSendingAgain() {
        for (int i = 0; i < 3; i++) {
            throttler.shouldSend("FILLED:AAPL");
        }
        assertFalse(throttler.shouldSend("FILLED:AAPL"));

        throttler.resetCounts();

        assertTrue(throttler.shouldSend("FILLED:AAPL"));
    }
}
