package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.strategy.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalConsumerServiceTest {

    @Mock
    private TradeSignalRepository signalRepository;

    @Mock
    private SignalValidationService validationService;

    @Mock
    private OrderPublisher orderPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Acknowledgment ack;

    private SignalConsumerService consumer;
    private ObjectMapper objectMapper;

    // Pre-built to avoid creating mocks inside a when().thenReturn() argument,
    // which would cause UnfinishedStubbingException (matches api-gateway test pattern).
    private CompletableFuture<SendResult<String, String>> successFuture;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new SignalConsumerService(
                objectMapper, signalRepository, validationService, orderPublisher, kafkaTemplate);

        successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private TradeSignal buildSignal(UUID id) {
        TradeSignal s = new TradeSignal();
        try {
            var field = TradeSignal.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        s.setSymbol("AAPL");
        s.setAction(TradeSignal.SignalAction.BUY);
        s.setPrice(BigDecimal.valueOf(150));
        s.setStopLoss(BigDecimal.valueOf(145));
        s.setTakeProfit(BigDecimal.valueOf(165));
        s.setStrategy("FVG");
        s.setStatus(TradeSignal.SignalStatus.PENDING);
        s.setCreatedAt(Instant.now());
        return s;
    }

    private String messageJson(UUID id) throws Exception {
        return objectMapper.writeValueAsString(buildSignal(id));
    }

    @Test
    void consume_validatedSignal_savesAndPublishesOrder() throws Exception {
        UUID id = UUID.randomUUID();
        TradeSignal signal = buildSignal(id);
        OrderDTO order = new OrderDTO(id, "AAPL", "NASDAQ", "STK",
                Order.OrderSide.BUY, Order.OrderType.LIMIT,
                BigDecimal.TEN, BigDecimal.valueOf(150),
                BigDecimal.valueOf(145), BigDecimal.valueOf(165),
                "FVG", Instant.now().plusSeconds(300));

        when(signalRepository.findById(id)).thenReturn(Optional.of(signal));
        when(validationService.validate(signal))
                .thenReturn(new ValidationResult(true, null, order, List.of()));
        when(signalRepository.save(any())).thenReturn(signal);

        consumer.consume(messageJson(id), "AAPL", ack);

        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        verify(signalRepository).save(captor.capture());
        assertEquals(TradeSignal.SignalStatus.VALIDATED, captor.getValue().getStatus());
        verify(orderPublisher).publishOrder(order);
        verify(ack).acknowledge();
    }

    @Test
    void consume_rejectedSignal_savesRejectionAndPublishesAlert() throws Exception {
        UUID id = UUID.randomUUID();
        TradeSignal signal = buildSignal(id);

        when(signalRepository.findById(id)).thenReturn(Optional.of(signal));
        when(validationService.validate(signal))
                .thenReturn(new ValidationResult(false, "Outside Kill Zone", null, List.of()));
        when(signalRepository.save(any())).thenReturn(signal);

        consumer.consume(messageJson(id), "AAPL", ack);

        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        verify(signalRepository).save(captor.capture());
        assertEquals(TradeSignal.SignalStatus.REJECTED, captor.getValue().getStatus());
        assertEquals("Outside Kill Zone", captor.getValue().getRejectionReason());
        verify(orderPublisher).publishRejection(any(RejectionEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    void consume_alreadyProcessedSignal_skipsAsDuplicate() throws Exception {
        UUID id = UUID.randomUUID();
        TradeSignal signal = buildSignal(id);
        signal.setStatus(TradeSignal.SignalStatus.VALIDATED);

        when(signalRepository.findById(id)).thenReturn(Optional.of(signal));

        consumer.consume(messageJson(id), "AAPL", ack);

        verify(validationService, never()).validate(any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_signalNotInDb_skipsProcessing() throws Exception {
        UUID id = UUID.randomUUID();
        when(signalRepository.findById(id)).thenReturn(Optional.empty());

        consumer.consume(messageJson(id), "AAPL", ack);

        verify(validationService, never()).validate(any());
        verify(ack).acknowledge();
    }

    @Test
    void consume_processingException_routesToDlqWithPublishFailedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        TradeSignal signal = buildSignal(id);

        when(signalRepository.findById(id)).thenReturn(Optional.of(signal));
        when(validationService.validate(any())).thenThrow(new RuntimeException("Unexpected error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successFuture);
        when(signalRepository.save(any())).thenReturn(signal);

        consumer.consume(messageJson(id), "AAPL", ack);

        verify(kafkaTemplate).send(eq("tradie.signals.dlq"), anyString(), anyString());
        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        verify(signalRepository).save(captor.capture());
        assertEquals(TradeSignal.SignalStatus.PUBLISH_FAILED, captor.getValue().getStatus());
        verify(ack).acknowledge();
    }

    @Test
    void consume_malformedMessage_missingId_routesToDlq() throws Exception {
        String malformedMessage = "{\"symbol\":\"AAPL\"}";
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successFuture);

        consumer.consume(malformedMessage, "AAPL", ack);

        verify(kafkaTemplate).send(eq("tradie.signals.dlq"), anyString(), anyString());
        verify(signalRepository, never()).findById(any());
        verify(ack).acknowledge();
    }
}
