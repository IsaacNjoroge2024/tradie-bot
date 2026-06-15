package com.tradie.alert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradie.alert.client.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Polls the Telegram Bot API for incoming commands every 2 seconds.
 * Persists the last processed update ID in Redis so polling resumes correctly after restart.
 * Only active when {@code tradie.telegram.enabled=true}.
 */
@ConditionalOnProperty(name = "tradie.telegram.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class TelegramPollingService {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);
    private static final String LAST_UPDATE_KEY = "telegram:last_update_id";

    private final TelegramClient telegramClient;
    private final TelegramCommandHandler commandHandler;
    private final StringRedisTemplate redisTemplate;

    private volatile long lastUpdateId = 0;

    public TelegramPollingService(
            TelegramClient telegramClient,
            TelegramCommandHandler commandHandler,
            StringRedisTemplate redisTemplate) {
        this.telegramClient = telegramClient;
        this.commandHandler = commandHandler;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void init() {
        try {
            String stored = redisTemplate.opsForValue().get(LAST_UPDATE_KEY);
            if (stored != null) {
                lastUpdateId = Long.parseLong(stored);
                log.info("Telegram polling resumed from update_id={}", lastUpdateId);
            }
        } catch (Exception e) {
            log.warn("Could not restore last Telegram update ID from Redis: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 2000)
    void pollUpdates() {
        try {
            JsonNode response = telegramClient.getUpdates(lastUpdateId + 1);
            if (!response.path("ok").asBoolean(false)) return;

            JsonNode results = response.path("result");
            if (results.isMissingNode() || !results.isArray()) return;

            long maxProcessedId = lastUpdateId;
            for (JsonNode update : results) {
                processUpdate(update);
                long updateId = update.path("update_id").asLong(0);
                if (updateId > maxProcessedId) {
                    maxProcessedId = updateId;
                }
            }

            if (maxProcessedId > lastUpdateId) {
                lastUpdateId = maxProcessedId;
                persistLastUpdateId();
            }

        } catch (Exception e) {
            log.error("Error during Telegram polling: {}", e.getMessage());
        }
    }

    private void processUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;

        String text   = message.path("text").asText("");
        String chatId = message.path("chat").path("id").asText("");

        if (!text.isEmpty() && !chatId.isEmpty()) {
            log.debug("Command received: '{}' from chatId={}", text, chatId);
            commandHandler.handle(text, chatId);
        }
    }

    private void persistLastUpdateId() {
        try {
            redisTemplate.opsForValue().set(LAST_UPDATE_KEY, String.valueOf(lastUpdateId));
        } catch (Exception e) {
            log.warn("Could not persist last Telegram update ID: {}", e.getMessage());
        }
    }
}
