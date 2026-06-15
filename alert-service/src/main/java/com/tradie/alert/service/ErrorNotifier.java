package com.tradie.alert.service;

import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Sends critical error notifications to Telegram.
 * Includes a trimmed stack trace snippet to aid debugging without flooding the chat.
 */
@Service
public class ErrorNotifier {

    private static final Logger log = LoggerFactory.getLogger(ErrorNotifier.class);
    private static final int STACK_TRACE_MAX_CHARS = 300;

    private final TelegramClient telegramClient;
    private final MessageFormatter messageFormatter;
    private final TelegramProperties properties;

    public ErrorNotifier(
            TelegramClient telegramClient,
            MessageFormatter messageFormatter,
            TelegramProperties properties) {
        this.telegramClient = telegramClient;
        this.messageFormatter = messageFormatter;
        this.properties = properties;
    }

    /**
     * Sends a system alert to Telegram for a critical error.
     *
     * @param service   the service where the error occurred (e.g. "IBKR", "Strategy Engine")
     * @param error     short description of the error
     * @param exception the exception, or null if not available
     */
    public void notifyError(String service, String error, Throwable exception) {
        notifySystemAlert(service, "ERROR", error, exception);
    }

    /**
     * Sends a system alert to Telegram with a custom type.
     *
     * @param service   the service where the alert occurred
     * @param type      the type of system alert (e.g. "CONNECTION_LOST")
     * @param messageText short description of the alert
     * @param exception the exception, or null if not available
     */
    public void notifySystemAlert(String service, String type, String messageText, Throwable exception) {
        if (!properties.getAlerts().isSystemAlerts()) return;

        try {
            String message = messageFormatter.formatSystemAlert(service, type, messageText);

            if (exception != null && properties.isDebugTraces()) {
                String trace = trimStackTrace(exception);
                message = message + "\n\n```\n" + escapeCode(trace) + "\n```";
            }

            telegramClient.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send system alert notification to Telegram: {}", e.getMessage());
        }
    }

    private String trimStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > STACK_TRACE_MAX_CHARS
                ? full.substring(0, STACK_TRACE_MAX_CHARS) + "..."
                : full;
    }

    private String escapeCode(String text) {
        // Inside MarkdownV2 code block, only ` and \ need escaping
        return text.replace("\\", "\\\\").replace("`", "\\`");
    }
}
