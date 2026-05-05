package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.strategy.dto.RejectionEvent;
import com.tradie.strategy.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SignalConsumerService {

    private static final Logger log = LoggerFactory.getLogger(SignalConsumerService.class);
    private static final String DLQ_TOPIC = "tradie.signals.dlq";

    private final ObjectMapper objectMapper;
    private final TradeSignalRepository signalRepository;
    private final SignalValidationService validationService;
    private final OrderPublisher orderPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SignalConsumerService(
            ObjectMapper objectMapper,
            TradeSignalRepository signalRepository,
            SignalValidationService validationService,
            OrderPublisher orderPublisher,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.signalRepository = signalRepository;
        this.validationService = validationService;
        this.orderPublisher = orderPublisher;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "tradie.signals", groupId = "strategy-engine-group")
    public void consume(
            String message,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        log.info("Signal received from Kafka, key={}", key);
        TradeSignal signal = null;

        try {
            JsonNode node = objectMapper.readTree(message);
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull()) {
                log.warn("Malformed Kafka message: missing 'id' field, routing to DLQ. key={}", key);
                try {
                    kafkaTemplate.send(DLQ_TOPIC, key, message).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception dlqEx) {
                    log.error("Failed to send malformed message to DLQ: {}", dlqEx.getMessage());
                }
                ack.acknowledge();
                return;
            }
            UUID signalId = UUID.fromString(idNode.asText());

            signal = signalRepository.findById(signalId).orElse(null);
            if (signal == null) {
                log.warn("Signal {} not found in DB, skipping", signalId);
                ack.acknowledge();
                return;
            }

            if (signal.getProcessedAt() != null
                    || signal.getStatus() == TradeSignal.SignalStatus.VALIDATED
                    || signal.getStatus() == TradeSignal.SignalStatus.EXECUTED
                    || signal.getStatus() == TradeSignal.SignalStatus.REJECTED
                    || signal.getStatus() == TradeSignal.SignalStatus.EXPIRED) {
                log.info("Signal {} already processed (status={}), skipping duplicate delivery",
                        signalId, signal.getStatus());
                ack.acknowledge();
                return;
            }

            log.info("Processing signal id={} symbol={} action={}",
                    signal.getId(), signal.getSymbol(), signal.getAction());

            ValidationResult result = validationService.validate(signal);

            if (result.approved()) {
                signal.setStatus(TradeSignal.SignalStatus.VALIDATED);
                signal.setProcessedAt(Instant.now());
                signalRepository.save(signal);
                orderPublisher.publishOrder(result.order());
            } else {
                signal.setStatus(TradeSignal.SignalStatus.REJECTED);
                signal.setRejectionReason(result.rejectionReason());
                signal.setProcessedAt(Instant.now());
                signalRepository.save(signal);
                orderPublisher.publishRejection(new RejectionEvent(
                        signal.getId(),
                        signal.getSymbol(),
                        signal.getAction().name(),
                        signal.getStrategy(),
                        result.rejectionReason(),
                        Instant.now()
                ));
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process signal, routing to DLQ: {}", e.getMessage(), e);
            try {
                kafkaTemplate.send(DLQ_TOPIC, key, message).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception dlqEx) {
                log.error("Failed to publish to DLQ: {}", dlqEx.getMessage());
            }

            if (signal != null) {
                try {
                    signal.setStatus(TradeSignal.SignalStatus.PUBLISH_FAILED);
                    signal.setRejectionReason("Processing error: " + e.getMessage());
                    signal.setProcessedAt(Instant.now());
                    signalRepository.save(signal);
                } catch (Exception saveEx) {
                    log.error("Failed to persist error status for signal: {}", saveEx.getMessage());
                }
            }
            ack.acknowledge();
        }
    }
}
