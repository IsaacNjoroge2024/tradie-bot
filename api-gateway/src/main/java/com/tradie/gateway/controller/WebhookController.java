package com.tradie.gateway.controller;

import com.tradie.gateway.dto.TradingViewSignal;
import com.tradie.gateway.service.SignalIngestionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final SignalIngestionService signalService;
    private final String webhookSecret;

    public WebhookController(
            SignalIngestionService signalService,
            @Value("${tradie.webhook.secret}") String webhookSecret) {
        this.signalService = signalService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/tradingview")
    public ResponseEntity<?> receiveTradingViewSignal(
            @Valid @RequestBody TradingViewSignal signal,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (!webhookSecret.equals(signal.authToken())) {
            log.warn("Unauthorized webhook attempt for symbol: {} from IP: {}",
                    signal.symbol(), getClientIp());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid authentication token"));
        }

        log.info("SIGNAL RECEIVED: {} {} @ {} [Strategy: {}, Timeframe: {}]",
                signal.action(), signal.symbol(), signal.price(),
                signal.strategy(), signal.timeframe());

        try {
            String signalId = signalService.processIncomingSignal(signal);

            return ResponseEntity.ok(Map.of(
                    "status", "acknowledged",
                    "signal_id", signalId,
                    "message", "Signal queued for processing"
            ));

        } catch (Exception e) {
            log.error("Failed to process signal: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process signal"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "api-gateway",
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    private String getClientIp() {
        // Implementation would use HttpServletRequest
        return "unknown";
    }
}
