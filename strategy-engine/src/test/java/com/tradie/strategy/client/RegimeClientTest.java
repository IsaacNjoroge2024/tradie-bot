package com.tradie.strategy.client;

import com.sun.net.httpserver.HttpServer;
import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.OHLCVId;
import com.tradie.strategy.dto.RegimeResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RegimeClientTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private HttpServer stubServer;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    }

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
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

    @Test
    void detectRegime_successfulResponse_deserializesAllFieldsCorrectly() throws Exception {
        String responseJson = """
                {
                  "symbol": "AAPL",
                  "timeframe": "1H",
                  "regime": "trending_up",
                  "probability": 0.87,
                  "duration_bars": 6,
                  "all_probabilities": {
                    "trending_up": 0.87,
                    "trending_down": 0.01,
                    "ranging": 0.1,
                    "volatile": 0.02
                  },
                  "recommendation": {
                    "position_size_multiplier": 1.0,
                    "preferred_strategies": ["TREND_FOLLOWING", "MOMENTUM"],
                    "avoid_strategies": ["MEAN_REVERSION"],
                    "stop_loss_multiplier": 1.2,
                    "take_profit_multiplier": 1.5,
                    "max_positions": 5,
                    "notes": "Bullish trend detected."
                  }
                }
                """;

        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.createContext("/api/regime/detect", exchange -> {
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        stubServer.start();

        RegimeClient client = new RegimeClient(
                WebClient.builder(),
                circuitBreakerRegistry,
                "http://localhost:" + stubServer.getAddress().getPort());
        ReflectionTestUtils.setField(client, "timeoutMs", 2000);

        RegimeResponse response = client.detectRegime("AAPL", "1H", oneCandle());

        assertEquals("AAPL", response.symbol());
        assertEquals("1H", response.timeframe());
        assertEquals("trending_up", response.regime());
        assertEquals(0.87, response.probability());
        assertEquals(6, response.durationBars());
        assertEquals(0.87, response.allProbabilities().get("trending_up"));
        assertEquals(1.0, response.recommendation().positionSizeMultiplier());
        assertEquals(List.of("TREND_FOLLOWING", "MOMENTUM"), response.recommendation().preferredStrategies());
        assertEquals(List.of("MEAN_REVERSION"), response.recommendation().avoidStrategies());
        assertEquals(1.2, response.recommendation().stopLossMultiplier());
        assertEquals(1.5, response.recommendation().takeProfitMultiplier());
        assertEquals(5, response.recommendation().maxPositions());
        assertEquals("Bullish trend detected.", response.recommendation().notes());
    }
}
