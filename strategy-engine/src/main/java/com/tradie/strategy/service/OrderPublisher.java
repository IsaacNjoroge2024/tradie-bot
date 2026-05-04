package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.strategy.dto.OrderDTO;
import com.tradie.strategy.dto.RejectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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
        kafkaTemplate.send(ORDERS_TOPIC, order.symbol(), json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order for signal {} to {}: {}",
                                order.signalId(), ORDERS_TOPIC, ex.getMessage());
                    } else {
                        log.info("Order for signal {} published to {} partition {}",
                                order.signalId(), ORDERS_TOPIC,
                                result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishRejection(RejectionEvent event) throws Exception {
        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(ALERTS_TOPIC, event.symbol(), json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish rejection event for signal {}: {}",
                                event.signalId(), ex.getMessage());
                    } else {
                        log.debug("Rejection event for signal {} published to alerts",
                                event.signalId());
                    }
                });
    }
}
