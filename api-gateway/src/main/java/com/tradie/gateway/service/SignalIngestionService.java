package com.tradie.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.common.service.AuditLogger;
import com.tradie.gateway.dto.TradingViewSignal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final Counter signalsReceivedCounter;
    private final AuditLogger auditLogger;

    public SignalIngestionService(
            TradeSignalRepository signalRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AuditLogger auditLogger) {
        this.signalRepository = signalRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
        this.signalsReceivedCounter = Counter.builder("tradie.signals.received")
                .description("Number of webhook signals received from TradingView")
                .tag("source", "tradingview")
                .register(meterRegistry);
    }

    @Transactional
    public String processIncomingSignal(TradingViewSignal tvSignal) throws Exception {
        signalsReceivedCounter.increment();
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
        // Normalise to 0.0–1.0: accept both decimal fraction (0.8) and percentage (80)
        if (tvSignal.confidence() != null) {
            double raw = tvSignal.confidence();
            signal.setConfidenceScore(raw > 1.0 ? raw / 100.0 : raw);
        }
        signal.setRawPayload(objectMapper.writeValueAsString(tvSignal));

        TradeSignal saved = signalRepository.save(signal);

        String messageKey = saved.getSymbol();
        String messageValue = objectMapper.writeValueAsString(saved);

        // Publish to Kafka and emit the audit event only after the DB transaction commits
        // successfully, preventing an audit entry for a signal that was never persisted.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditLogger.logSignalReceived("api-gateway", saved);
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
