package com.tradie.strategy.client;

import com.tradie.strategy.dto.MarketStatusResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class NewsShieldClient {

    private static final Logger log = LoggerFactory.getLogger(NewsShieldClient.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    @Value("${tradie.news-shield.timeout-ms:5000}")
    private int timeoutMs;

    public NewsShieldClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${tradie.news-shield.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("newsShield");
    }

    @Cacheable(value = "marketStatus", key = "#symbol != null ? #symbol : 'global'")
    public MarketStatusResponse getMarketStatus(String symbol) {
        try {
            MarketStatusResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/market-status");
                        if (symbol != null && !symbol.isBlank()) {
                            builder.queryParam("symbol", symbol);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(MarketStatusResponse.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .block();

            log.debug("News Shield response for {}: safe={}, risk={}", symbol,
                    response != null ? response.safeToTrade() : "null",
                    response != null ? response.riskLevel() : "null");
            return response != null ? response : fallbackMarketStatus();
        } catch (Exception e) {
            log.warn("News Shield unavailable for symbol={}, applying safe fallback: {}", symbol, e.getMessage());
            return fallbackMarketStatus();
        }
    }

    private MarketStatusResponse fallbackMarketStatus() {
        return new MarketStatusResponse(true, "LOW",
                List.of("News Shield unavailable - fallback applied"));
    }
}
