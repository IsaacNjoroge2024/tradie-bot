package com.tradie.alert.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import com.tradie.alert.service.AlertThrottler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertConsumerTest {

    @Mock private TelegramClient telegramClient;
    @Mock private MessageFormatter messageFormatter;
    @Mock private AlertThrottler alertThrottler;
    @Mock private Acknowledgment ack;

    private AlertConsumer consumer;
    private TelegramProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties   = new TelegramProperties();
        consumer     = new AlertConsumer(objectMapper, telegramClient, messageFormatter, alertThrottler, properties);
    }

    @Test
    void consume_filledEvent_sendsAlert() throws Exception {
        String message = """
                {"type":"FILLED","symbol":"AAPL","side":"BUY","price":150.50,
                "quantity":100,"strategy":"FVG","timestamp":"2025-01-15T15:32:15Z"}""";

        when(alertThrottler.shouldSend("FILLED:AAPL")).thenReturn(true);
        when(messageFormatter.formatOrderEvent(any())).thenReturn("formatted message");

        consumer.consume(message, "AAPL", ack);

        verify(telegramClient).sendMessage("formatted message");
        verify(ack).acknowledge();
    }

    @Test
    void consume_throttledAlert_doesNotSend() throws Exception {
        String message = """
                {"type":"FILLED","symbol":"AAPL","side":"BUY","price":150.50,
                "quantity":100,"strategy":"FVG","timestamp":"2025-01-15T15:32:15Z"}""";

        when(alertThrottler.shouldSend("FILLED:AAPL")).thenReturn(false);

        consumer.consume(message, "AAPL", ack);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(ack).acknowledge();
    }

    @Test
    void consume_rejectionEvent_sendsAlert() throws Exception {
        String message = """
                {"symbol":"TSLA","action":"BUY","rejectionReason":"High-impact FOMC",
                "strategy":"FVG","timestamp":"2025-01-15T15:32:15Z"}""";

        when(alertThrottler.shouldSend("SIGNAL_REJECTED:TSLA")).thenReturn(true);
        when(messageFormatter.formatRejectionEvent(any())).thenReturn("rejection formatted");

        consumer.consume(message, "TSLA", ack);

        verify(telegramClient).sendMessage("rejection formatted");
        verify(ack).acknowledge();
    }

    @Test
    void consume_signalRejectedAlertsDisabled_doesNotSend() throws Exception {
        properties.getAlerts().setSignalRejected(false);

        String message = """
                {"symbol":"TSLA","action":"BUY","rejectionReason":"test",
                "strategy":"FVG","timestamp":"2025-01-15T15:32:15Z"}""";

        consumer.consume(message, "TSLA", ack);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(ack).acknowledge();
    }

    @Test
    void consume_unknownEventFormat_skipsAndAcknowledges() throws Exception {
        String message = "{\"unknown\":\"field\"}";

        consumer.consume(message, null, ack);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(ack).acknowledge();
    }

    @Test
    void consume_malformedJson_doesNotCrash() {
        consumer.consume("not-json", null, ack);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(ack).acknowledge();
    }

    @Test
    void consume_tradeExitAlertsDisabled_doesNotSendForTakeProfitHit() throws Exception {
        properties.getAlerts().setTradeExit(false);

        String message = """
                {"type":"TAKE_PROFIT_HIT","symbol":"AAPL","side":"BUY","price":165.00,
                "quantity":100,"strategy":"FVG","timestamp":"2025-01-15T15:32:15Z"}""";

        consumer.consume(message, "AAPL", ack);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(ack).acknowledge();
    }
}
