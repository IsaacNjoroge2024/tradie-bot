package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradie.strategy.dto.OrderDTO;
import com.tradie.strategy.dto.RejectionEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class OrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderPublisher.class);
    private static final String ORDERS_TOPIC = "tradie.orders";
    private static final String ALERTS_TOPIC = "tradie.alerts";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishOrder(OrderDTO order) throws Exception {
        String json = objectMapper.writeValueAsString(order);
        var result = kafkaTemplate.send(ORDERS_TOPIC, order.symbol(), json).get(5, TimeUnit.SECONDS);
        log.info("Order for signal {} published to {} partition {}",
                order.signalId(), ORDERS_TOPIC, result.getRecordMetadata().partition());
    }

    public void publishRejection(RejectionEvent event) throws Exception {
        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(ALERTS_TOPIC, event.symbol(), json).get(5, TimeUnit.SECONDS);
        log.debug("Rejection event for signal {} published to alerts", event.signalId());
    }

    public void publishSystemAlert(String service, String type, String message) {
        try {
            String json = objectMapper.writeValueAsString(buildSystemAlertJson(service, type, message));
            kafkaTemplate.send(ALERTS_TOPIC, service, json);
            log.info("System alert published: service={} type={}", service, type);
        } catch (Exception e) {
            log.error("Failed to serialize system alert service={} type={}: {}",
                    service, type, e.getMessage(), e);
        }
    }

    @PostConstruct
    public void onStart() {
        publishSystemAlert("Strategy Engine", "SERVICE_START", "Service started successfully");
    }

    @PreDestroy
    public void onStop() {
        try {
            kafkaTemplate.send(ALERTS_TOPIC, "Strategy Engine",
                    objectMapper.writeValueAsString(
                            buildSystemAlertJson("Strategy Engine", "SERVICE_SHUTDOWN", "Service is shutting down")))
                    .get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to send shutdown alert: {}", e.getMessage());
        }
    }

    private ObjectNode buildSystemAlertJson(String service, String type, String message) {
        var node = objectMapper.createObjectNode();
        node.put("service", service);
        node.put("type", type);
        node.put("message", message);
        node.put("timestamp", java.time.Instant.now().toString());
        return node;
    }
}
