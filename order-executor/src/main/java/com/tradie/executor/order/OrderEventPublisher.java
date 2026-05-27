package com.tradie.executor.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.executor.dto.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Publishes {@link OrderEvent} records to the {@code tradie.alerts} Kafka topic.
 * These events are consumed by the Alert Service (Ticket 12) to send Telegram notifications.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String ALERTS_TOPIC = "tradie.alerts";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an order lifecycle event to the alerts topic.
     * Failures are logged but not propagated so order processing continues uninterrupted.
     *
     * @param event the order event to publish
     */
    public void publishOrderEvent(OrderEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ALERTS_TOPIC, event.symbol(), json).get(5, TimeUnit.SECONDS);
            log.info("Order event published: type={} signal={} symbol={}",
                    event.type(), event.signalId(), event.symbol());
        } catch (Exception e) {
            log.error("Failed to publish order event type={} signal={}: {}",
                    event.type(), event.signalId(), e.getMessage(), e);
        }
    }
}
