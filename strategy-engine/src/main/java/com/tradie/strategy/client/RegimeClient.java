package com.tradie.strategy.client;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.strategy.dto.PriceDataDTO;
import com.tradie.strategy.dto.RegimeDetectRequest;
import com.tradie.strategy.dto.RegimeResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class RegimeClient {

    private static final Logger log = LoggerFactory.getLogger(RegimeClient.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    @Value("${tradie.ml-service.timeout-ms:5000}")
    private int timeoutMs;

    public RegimeClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Value("${tradie.ml-service.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("mlService");
    }

    public RegimeResponse detectRegime(String symbol, String timeframe, List<OHLCVCandle> candles) {
        RegimeDetectRequest request = new RegimeDetectRequest(symbol, timeframe, toPriceData(candles));

        RegimeResponse response = webClient.post()
                .uri("/api/regime/detect")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RegimeResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .block();

        if (response == null) {
            throw new IllegalStateException("ML Service returned null regime response for symbol: " + symbol);
        }
        log.debug("Regime for {}:{}: {} (p={})", symbol, timeframe, response.regime(), response.probability());
        return response;
    }

    private PriceDataDTO toPriceData(List<OHLCVCandle> candles) {
        List<String> timestamp = new ArrayList<>(candles.size());
        List<Double> open = new ArrayList<>(candles.size());
        List<Double> high = new ArrayList<>(candles.size());
        List<Double> low = new ArrayList<>(candles.size());
        List<Double> close = new ArrayList<>(candles.size());
        List<Double> volume = new ArrayList<>(candles.size());

        for (OHLCVCandle candle : candles) {
            timestamp.add(candle.getId().getTime().toString());
            open.add(candle.getOpen());
            high.add(candle.getHigh());
            low.add(candle.getLow());
            close.add(candle.getClose());
            volume.add((double) candle.getVolume());
        }

        return new PriceDataDTO(timestamp, open, high, low, close, volume);
    }
}
