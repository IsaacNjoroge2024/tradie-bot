package com.tradie.strategy.client;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.OHLCVId;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RegimeClientTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    }

    private List<OHLCVCandle> oneCandle() {
        OHLCVCandle c = new OHLCVCandle();
        c.setId(new OHLCVId(Instant.now(), "AAPL", "NASDAQ", "1H"));
        c.setOpen(100);
        c.setHigh(101);
        c.setLow(99);
        c.setClose(100.5);
        c.setVolume(1000);
        return List.of(c);
    }

    @Test
    void detectRegime_whenMlServiceDown_throwsException() {
        RegimeClient client = new RegimeClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                "http://localhost:19998");
        ReflectionTestUtils.setField(client, "timeoutMs", 500);

        assertThrows(Exception.class, () -> client.detectRegime("AAPL", "1H", oneCandle()));
    }

    @Test
    void detectRegime_circuitBreakerRegistersFailures() {
        RegimeClient client = new RegimeClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                "http://localhost:19998");
        ReflectionTestUtils.setField(client, "timeoutMs", 200);

        for (int i = 0; i < 3; i++) {
            assertThrows(Exception.class, () -> client.detectRegime("AAPL", "1H", oneCandle()));
        }

        var cb = circuitBreakerRegistry.circuitBreaker("mlService");
        assertTrue(cb.getMetrics().getNumberOfFailedCalls() >= 3,
                "Circuit breaker should register failed calls");
    }
}
