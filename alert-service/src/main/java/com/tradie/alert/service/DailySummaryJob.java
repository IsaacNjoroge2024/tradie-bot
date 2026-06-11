package com.tradie.alert.service;

import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends a daily P&L summary to Telegram at 5 PM EST on weekdays.
 * Only active when {@code tradie.telegram.enabled=true}.
 */
@ConditionalOnProperty(name = "tradie.telegram.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class DailySummaryJob {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryJob.class);

    private final DailySummaryService dailySummaryService;
    private final MessageFormatter messageFormatter;
    private final TelegramClient telegramClient;
    private final TelegramProperties properties;

    public DailySummaryJob(
            DailySummaryService dailySummaryService,
            MessageFormatter messageFormatter,
            TelegramClient telegramClient,
            TelegramProperties properties) {
        this.dailySummaryService = dailySummaryService;
        this.messageFormatter = messageFormatter;
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    @Scheduled(cron = "${tradie.telegram.summary.cron:0 0 17 * * MON-FRI}", zone = "${tradie.telegram.summary.timezone:America/New_York}")
    public void sendDailySummary() {
        if (!properties.getSummary().isEnabled()) {
            log.debug("Daily summary is disabled, skipping");
            return;
        }
        log.info("Sending daily P&L summary");
        try {
            DailySummaryService.DailySummaryData summary = dailySummaryService.getDailySummary();
            String message = messageFormatter.formatDailySummary(summary);
            telegramClient.sendMessage(message);
            log.info("Daily summary sent: trades={} pnl={}", summary.totalTrades(), summary.totalPnl());
        } catch (Exception e) {
            log.error("Failed to send daily summary: {}", e.getMessage(), e);
        }
    }
}
