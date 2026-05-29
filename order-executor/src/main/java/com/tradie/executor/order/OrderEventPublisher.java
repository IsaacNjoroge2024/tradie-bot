package com.tradie.executor.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.executor.dto.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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
            @Value("${kafka.topics.alerts}") String alertsTopic) {
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
}
