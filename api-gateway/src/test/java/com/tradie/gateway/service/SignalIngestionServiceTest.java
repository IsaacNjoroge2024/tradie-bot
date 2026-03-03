package com.tradie.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradie.common.entity.TradeSignal;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.gateway.dto.TradingViewSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalIngestionServiceTest {

    @Mock
    private TradeSignalRepository signalRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SignalIngestionService signalIngestionService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        signalIngestionService = new SignalIngestionService(signalRepository, kafkaTemplate, objectMapper);
    }

    private TradingViewSignal buildValidSignal() {
        return new TradingViewSignal(
                "AAPL", "BUY", "FVG", 150.0, 148.0, 155.0,
                "test-secret", "NASDAQ", "15m", 85.0);
    }

    private TradeSignal buildSavedSignal(UUID id, String symbol) throws Exception {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol(symbol);
        Field idField = TradeSignal.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(signal, id);
        return signal;
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_persistsSignalToDatabase() throws Exception {
        UUID signalId = UUID.randomUUID();
        when(signalRepository.save(any(TradeSignal.class))).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        signalIngestionService.processIncomingSignal(buildValidSignal());

        verify(signalRepository, times(1)).save(any(TradeSignal.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_publishesToKafka() throws Exception {
        UUID signalId = UUID.randomUUID();
        when(signalRepository.save(any(TradeSignal.class))).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        signalIngestionService.processIncomingSignal(buildValidSignal());

        verify(kafkaTemplate, times(1)).send(eq("tradie.signals"), eq("AAPL"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_mapsFieldsCorrectly() throws Exception {
        UUID signalId = UUID.randomUUID();
        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        when(signalRepository.save(captor.capture())).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        signalIngestionService.processIncomingSignal(buildValidSignal());

        TradeSignal captured = captor.getValue();
        assertEquals("AAPL", captured.getSymbol());
        assertEquals(TradeSignal.SignalAction.BUY, captured.getAction());
        assertEquals("FVG", captured.getStrategy());
        assertEquals(TradeSignal.SignalSource.TRADINGVIEW, captured.getSource());
        assertEquals(BigDecimal.valueOf(150.0), captured.getPrice());
        assertEquals(BigDecimal.valueOf(148.0), captured.getStopLoss());
        assertEquals(BigDecimal.valueOf(155.0), captured.getTakeProfit());
        assertEquals(0.85, captured.getConfidenceScore(), 0.001);
        assertEquals("NASDAQ", captured.getExchange());
        assertEquals("15m", captured.getTimeframe());
        assertNotNull(captured.getRawPayload());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_returnsSignalId() throws Exception {
        UUID signalId = UUID.randomUUID();
        when(signalRepository.save(any(TradeSignal.class))).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        String result = signalIngestionService.processIncomingSignal(buildValidSignal());

        assertEquals(signalId.toString(), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_symbolIsUpperCased() throws Exception {
        TradingViewSignal lowerCaseSignal = new TradingViewSignal(
                "aapl", "BUY", "FVG", 150.0, 148.0, 155.0,
                "test-secret", "NASDAQ", "15m", null);

        UUID signalId = UUID.randomUUID();
        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        when(signalRepository.save(captor.capture())).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        signalIngestionService.processIncomingSignal(lowerCaseSignal);

        assertEquals("AAPL", captor.getValue().getSymbol());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processIncomingSignal_nullConfidence_setsNullConfidenceScore() throws Exception {
        TradingViewSignal signalWithNoConfidence = new TradingViewSignal(
                "AAPL", "SELL", "OrderBlock", 150.0, 148.0, 155.0,
                "test-secret", null, null, null);

        UUID signalId = UUID.randomUUID();
        ArgumentCaptor<TradeSignal> captor = ArgumentCaptor.forClass(TradeSignal.class);
        when(signalRepository.save(captor.capture())).thenReturn(buildSavedSignal(signalId, "AAPL"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        signalIngestionService.processIncomingSignal(signalWithNoConfidence);

        assertNull(captor.getValue().getConfidenceScore());
        // Defaults applied by TradingViewSignal compact constructor
        assertEquals("SMART", captor.getValue().getExchange());
        assertEquals("15m", captor.getValue().getTimeframe());
    }
}
