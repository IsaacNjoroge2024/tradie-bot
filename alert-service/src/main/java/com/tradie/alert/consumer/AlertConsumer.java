package com.tradie.alert.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import com.tradie.alert.service.AlertThrottler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for the {@code tradie.alerts} topic.
 * Handles two event shapes published by other services:
 * <ul>
 *   <li>{@code OrderEvent} (from order-executor) — has a {@code type} field</li>
 *   <li>{@code RejectionEvent} (from strategy-engine) — has a {@code rejectionReason} field</li>
 * </ul>
 * Only active when {@code tradie.telegram.enabled=true}.
 */
@ConditionalOnProperty(name = "tradie.telegram.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final ObjectMapper objectMapper;
    private final TelegramClient telegramClient;
    private final MessageFormatter messageFormatter;
    private final AlertThrottler alertThrottler;
    private final TelegramProperties properties;

    public AlertConsumer(
            ObjectMapper objectMapper,
            TelegramClient telegramClient,
            MessageFormatter messageFormatter,
            AlertThrottler alertThrottler,
            TelegramProperties properties) {
        this.objectMapper = objectMapper;
        this.telegramClient = telegramClient;
        this.messageFormatter = messageFormatter;
        this.alertThrottler = alertThrottler;
        this.properties = properties;
    }

    @KafkaListener(topics = "tradie.alerts", groupId = "${spring.kafka.consumer.group-id:alert-service}")
    public void consume(
            String message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        log.debug("Alert received from Kafka, key={}", key);
        try {
            JsonNode node = objectMapper.readTree(message);
            String formatted = null;
            String throttleKey;

            if (node.has("type")) {
                // OrderEvent from order-executor
                String type   = node.path("type").asText();
                String symbol = node.path("symbol").asText("UNKNOWN");
                throttleKey   = type + ":" + symbol;

                boolean shouldSend = switch (type) {
                    case "FILLED"                 -> properties.getAlerts().isTradeEntry();
                    case "TAKE_PROFIT_HIT",
                         "STOP_LOSS_HIT",
                         "POSITION_CLOSED_MANUAL" -> properties.getAlerts().isTradeExit();
                    default                       -> false;
                };

                if (shouldSend && alertThrottler.shouldSend(throttleKey)) {
                    formatted = messageFormatter.formatOrderEvent(node);
                }

            } else if (node.has("rejectionReason")) {
                // RejectionEvent from strategy-engine
                String symbol = node.path("symbol").asText("UNKNOWN");
                throttleKey   = "SIGNAL_REJECTED:" + symbol;

                if (properties.getAlerts().isSignalRejected()
                        && alertThrottler.shouldSend(throttleKey)) {
                    formatted = messageFormatter.formatRejectionEvent(node);
                }

            } else {
                log.warn("Unknown alert event format, skipping: key={}", key);
            }

            if (formatted != null) {
                telegramClient.sendMessage(formatted);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process alert event: key={} error={}", key, e.getMessage(), e);
            ack.acknowledge();
        }
    }
}
