package com.tradie.executor.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.executor.dto.OrderEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Publishes {@link OrderEvent} records to the alerts Kafka topic.
 * These events are consumed by the Alert Service (Ticket 12) to send Telegram notifications.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String alertsTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${tradie.kafka.topics.alerts}") String alertsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.alertsTopic = alertsTopic;
    }

    /**
     * Publishes an order lifecycle event to the alerts topic asynchronously.
     * Failures are logged but not propagated so IBKR callback threads are never blocked.
     *
     * @param event the order event to publish
     */
    public void publishOrderEvent(OrderEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(alertsTopic, event.symbol(), json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Order event published: type={} signal={} symbol={}",
                                    event.type(), event.signalId(), event.symbol());
                        } else {
                            log.error("Failed to publish order event type={} signal={}: {}",
                                    event.type(), event.signalId(), ex.getMessage(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize order event type={} signal={}: {}",
                    event.type(), event.signalId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a system alert event to the alerts topic.
     *
     * @param service the source service (e.g., "IBKR")
     * @param type the type of alert (e.g., "CONNECTION_LOST")
     * @param message the alert details
     */
    public void publishSystemAlert(String service, String type, String message) {
        try {
            var node = objectMapper.createObjectNode();
            node.put("service", service);
            node.put("type", type);
            node.put("message", message);
            node.put("timestamp", java.time.Instant.now().toString());

            String json = objectMapper.writeValueAsString(node);
            kafkaTemplate.send(alertsTopic, service, json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("System alert published: service={} type={}", service, type);
                        } else {
                            log.error("Failed to publish system alert service={} type={}: {}",
                                    service, type, ex.getMessage(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize system alert service={} type={}: {}",
                    service, type, e.getMessage(), e);
        }
    }

    @PostConstruct
    public void onStart() {
        publishSystemAlert("Order Executor", "SERVICE_START", "Service started successfully");
    }

    @PreDestroy
    public void onStop() {
        try {
            var node = objectMapper.createObjectNode();
            node.put("service", "Order Executor");
            node.put("type", "SERVICE_SHUTDOWN");
            node.put("message", "Service is shutting down");
            node.put("timestamp", java.time.Instant.now().toString());
            kafkaTemplate.send(alertsTopic, "Order Executor", objectMapper.writeValueAsString(node))
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to send shutdown alert: {}", e.getMessage());
        }
    }
}
