package com.tradie.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.gateway.dto.TradingViewSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Service
public class SignalIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SignalIngestionService.class);
    private static final String SIGNALS_TOPIC = "tradie.signals";

    private final TradeSignalRepository signalRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SignalIngestionService(
            TradeSignalRepository signalRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.signalRepository = signalRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String processIncomingSignal(TradingViewSignal tvSignal) throws Exception {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol(tvSignal.symbol().toUpperCase());
        signal.setExchange(tvSignal.exchange());
        signal.setAction(TradeSignal.SignalAction.valueOf(tvSignal.action()));
        signal.setStrategy(tvSignal.strategy());
        signal.setSource(TradeSignal.SignalSource.TRADINGVIEW);
        signal.setPrice(BigDecimal.valueOf(tvSignal.price()));
        signal.setStopLoss(tvSignal.stopLoss() != null ? BigDecimal.valueOf(tvSignal.stopLoss()) : null);
        signal.setTakeProfit(tvSignal.takeProfit() != null ? BigDecimal.valueOf(tvSignal.takeProfit()) : null);
        signal.setTimeframe(tvSignal.timeframe());
        signal.setConfidenceScore(tvSignal.confidence() != null ? tvSignal.confidence() / 100.0 : null);
        signal.setRawPayload(objectMapper.writeValueAsString(tvSignal));

        TradeSignal saved = signalRepository.save(signal);

        String messageKey = saved.getSymbol();
        String messageValue = objectMapper.writeValueAsString(saved);

        // Publish to Kafka only after the DB transaction commits successfully,
        // preventing divergence where Kafka has the event but the DB does not (or vice versa).
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(SIGNALS_TOPIC, messageKey, messageValue)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to publish signal {} to Kafka: {}",
                                        saved.getId(), ex.getMessage());
                            } else {
                                log.debug("Signal {} published to Kafka partition {}",
                                        saved.getId(), result.getRecordMetadata().partition());
                            }
                        });
            }
        });

        return saved.getId().toString();
    }
}
