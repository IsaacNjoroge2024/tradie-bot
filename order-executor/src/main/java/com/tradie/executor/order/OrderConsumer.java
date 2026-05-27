package com.tradie.executor.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.executor.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer for the {@code tradie.orders} topic.
 *
 * <p>Validates that the order has not expired, then delegates to
 * {@link OrderSubmissionService} to construct and submit the bracket order to IBKR.
 * Failed messages are routed to {@code tradie.orders.dlq}.
 */
@Service
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private static final String DLQ_TOPIC = "tradie.orders.dlq";

    private final ObjectMapper objectMapper;
    private final OrderSubmissionService orderSubmissionService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderConsumer(
            ObjectMapper objectMapper,
            OrderSubmissionService orderSubmissionService,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.orderSubmissionService = orderSubmissionService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "tradie.orders", groupId = "order-executor")
    public void consume(
            String message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        log.info("Order received from Kafka, key={}", key);

        try {
            OrderDTO order = objectMapper.readValue(message, OrderDTO.class);

            // Validate order not expired
            if (order.validUntil() != null && Instant.now().isAfter(order.validUntil())) {
                log.warn("Order expired before processing: signal={} symbol={} validUntil={}",
                        order.signalId(), order.symbol(), order.validUntil());
                ack.acknowledge();
                return;
            }

            log.info("Processing order: signal={} symbol={} side={} qty={}",
                    order.signalId(), order.symbol(), order.side(), order.quantity());

            orderSubmissionService.submitBracketOrder(order);
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process order, routing to DLQ: {}", e.getMessage(), e);
            try {
                kafkaTemplate.send(DLQ_TOPIC, key, message).get(5, TimeUnit.SECONDS);
            } catch (Exception dlqEx) {
                log.error("Failed to send order to DLQ: {}", dlqEx.getMessage());
            }
            ack.acknowledge();
        }
    }
}
