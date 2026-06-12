package com.tradie.alert.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.alert.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for the Telegram Bot API.
 * Supports sending messages and polling for updates.
 * Implements retry with exponential backoff and basic rate limiting.
 */
@Service
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final String BASE_URL = "https://api.telegram.org";
    private static final int MAX_RETRIES = 3;
    private static final long MIN_SEND_INTERVAL_MS = 50; // ~20 msg/sec, safely under 30/sec limit

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private long lastSendTimeMs = 0;

    public TelegramClient(TelegramProperties properties, ObjectMapper objectMapper,
                          @Qualifier("telegramRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a message to the default configured chat using the configured parse mode.
     */
    public void sendMessage(String text) {
        sendMessage(properties.getChatId(), text);
    }

    /**
     * Sends a message to the specified chat using the configured parse mode.
     */
    public void sendMessage(String chatId, String text) {
        if (!properties.isEnabled()) {
            log.debug("Telegram disabled, skipping message send");
            return;
        }
        enforceRateLimit();
        doSendWithRetry(chatId, text, properties.getParseMode(), 0);
    }

    /**
     * Polls Telegram for new updates starting from the given offset.
     * Returns the raw API response as a JsonNode, or an empty object on error.
     */
    public JsonNode getUpdates(long offset) {
        if (!properties.isEnabled()) {
            return objectMapper.createObjectNode();
        }
        try {
            String url = BASE_URL + "/bot" + properties.getBotToken()
                    + "/getUpdates?offset=" + offset + "&timeout=0&limit=100";
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to get Telegram updates: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private void enforceRateLimit() {
        long sleepTime;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastSendTimeMs;
            sleepTime = elapsed < MIN_SEND_INTERVAL_MS ? MIN_SEND_INTERVAL_MS - elapsed : 0;
            lastSendTimeMs = now + sleepTime;
        }
        if (sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void doSendWithRetry(String chatId, String text, String parseMode, int attempt) {
        try {
            String url = BASE_URL + "/bot" + properties.getBotToken() + "/sendMessage";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", parseMode);

            String json = objectMapper.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            restTemplate.postForObject(url, entity, String.class);
            log.debug("Telegram message sent to chat={}", chatId);

        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                long delayMs = (long) Math.pow(2, attempt) * 1000L;
                log.warn("Telegram send failed (attempt {}), retrying in {}ms: {}",
                        attempt + 1, delayMs, e.getMessage());
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                doSendWithRetry(chatId, text, parseMode, attempt + 1);
            } else {
                log.error("Telegram send failed after {} retries: {}", MAX_RETRIES, e.getMessage());
            }
        }
    }
}
