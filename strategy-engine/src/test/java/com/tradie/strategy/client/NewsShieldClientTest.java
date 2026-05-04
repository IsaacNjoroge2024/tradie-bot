package com.tradie.strategy.client;

import com.tradie.strategy.dto.MarketStatusResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NewsShieldClientTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    }

    @Test
    void getMarketStatus_whenNewsShieldDown_returnsFallback() {
        // Point at a port that will refuse connection
        NewsShieldClient client = new NewsShieldClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                "http://localhost:19999");
        ReflectionTestUtils.setField(client, "timeoutMs", 500);

        MarketStatusResponse response = client.getMarketStatus("AAPL");

        assertTrue(response.safeToTrade());
        assertEquals("LOW", response.riskLevel());
        assertFalse(response.reasons().isEmpty());
        assertTrue(response.reasons().get(0).contains("fallback"));
    }

    @Test
    void getMarketStatus_circuitBreakerRegistersFailures() {
        NewsShieldClient client = new NewsShieldClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                "http://localhost:19999");
        ReflectionTestUtils.setField(client, "timeoutMs", 200);

        // Multiple calls should all return the safe fallback without throwing
        for (int i = 0; i < 3; i++) {
            MarketStatusResponse response = client.getMarketStatus("AAPL");
            assertTrue(response.safeToTrade());
        }
    }
}
