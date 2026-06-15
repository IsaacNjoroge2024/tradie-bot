package com.tradie.alert.service;

import com.tradie.alert.client.TelegramClient;
import com.tradie.alert.config.TelegramProperties;
import com.tradie.alert.formatter.MessageFormatter;
import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;

import static com.tradie.alert.util.TelegramEscaper.escape;

/**
 * Processes Telegram bot commands received via polling.
 * Each command handler queries live data and sends a formatted response.
 */
@Service
public class TelegramCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandHandler.class);

    private final TelegramClient telegramClient;
    private final TradingControlService tradingControlService;
    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final DailySummaryService dailySummaryService;
    private final MessageFormatter messageFormatter;
    private final RestTemplate restTemplate;
    private final String orderExecutorUrl;
    private final TelegramProperties properties;

    public TelegramCommandHandler(
            TelegramClient telegramClient,
            TradingControlService tradingControlService,
            PositionRepository positionRepository,
            OrderRepository orderRepository,
            DailySummaryService dailySummaryService,
            MessageFormatter messageFormatter,
            @Qualifier("telegramRestTemplate") RestTemplate restTemplate,
            @Value("${tradie.order-executor.url:http://localhost:8082}") String orderExecutorUrl,
            TelegramProperties properties) {
        this.telegramClient = telegramClient;
        this.tradingControlService = tradingControlService;
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.dailySummaryService = dailySummaryService;
        this.messageFormatter = messageFormatter;
        this.restTemplate = restTemplate;
        this.orderExecutorUrl = orderExecutorUrl;
        this.properties = properties;
    }

    /**
     * Routes a Telegram message to the appropriate command handler.
     *
     * @param text   the raw message text (e.g. "/pause manual stop")
     * @param chatId the Telegram chat ID to reply to
     */
    public void handle(String text, String chatId) {
        if (text == null || !text.startsWith("/")) return;

        String configuredChatId = properties.getChatId();
        if (configuredChatId != null && !configuredChatId.isBlank()
                && !chatId.equals(configuredChatId)) {
            log.warn("Rejected Telegram command from unauthorized chat: chatId={}", chatId);
            return;
        }

        String[] parts = text.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args    = parts.length > 1 ? parts[1].trim() : "";

        log.info("Telegram command received: {} from chatId={}", command, chatId);

        switch (command) {
            case "/status"    -> handleStatus(chatId);
            case "/positions" -> handlePositions(chatId);
            case "/pnl"       -> handlePnl(chatId);
            case "/pause"     -> handlePause(chatId, args);
            case "/resume"    -> handleResume(chatId);
            case "/risk"      -> handleRisk(chatId);
            case "/cancel"    -> handleCancel(chatId, args);
            case "/help"      -> handleHelp(chatId);
            default -> telegramClient.sendMessage(chatId,
                    "Unknown command\\. Type /help for the list of commands\\.");
        }
    }

    private void handleStatus(String chatId) {
        boolean paused   = tradingControlService.isPaused();
        String pauseInfo = paused ? "PAUSED ⏸️" : "ACTIVE ✅";
        String reason    = paused ? tradingControlService.getPauseReason() : null;
        long openCount   = positionRepository.countByStatus(Position.PositionStatus.OPEN);

        String connStatus = "UNKNOWN ❓";
        try {
            String url = orderExecutorUrl + "/api/ibkr/status";
            JsonNode statusResponse = restTemplate.getForObject(url, JsonNode.class);
            if (statusResponse != null && statusResponse.has("connected")) {
                boolean connected = statusResponse.path("connected").asBoolean();
                connStatus = connected ? "CONNECTED 🟢" : "DISCONNECTED 🔴";
            }
        } catch (Exception e) {
            log.warn("Failed to query IBKR status from order-executor: {}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🤖 *TRADIE STATUS*\n");
        sb.append(MessageFormatter.SEP).append("\n");
        sb.append("Trading: ").append(pauseInfo).append("\n");
        if (reason != null) sb.append("Pause Reason: ").append(escape(reason)).append("\n");
        sb.append("IBKR Connection: ").append(connStatus).append("\n");
        sb.append("Open Positions: ").append(escape(String.valueOf(openCount))).append("\n");
        sb.append(MessageFormatter.SEP);

        telegramClient.sendMessage(chatId, sb.toString());
    }

    private void handlePositions(String chatId) {
        List<Position> positions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        if (positions.isEmpty()) {
            telegramClient.sendMessage(chatId, "📭 No open positions\\.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📈 *OPEN POSITIONS*\n");
        sb.append(MessageFormatter.SEP).append("\n");

        int i = 1;
        for (Position p : positions) {
            String side  = p.getSide() != null ? p.getSide().name() : "?";
            String qty   = p.getQuantity() != null ? String.format(Locale.US, "%.0f", p.getQuantity().doubleValue()) : "?";
            String entry = p.getEntryPrice() != null ? String.format(Locale.US, "%.2f", p.getEntryPrice().doubleValue()) : "?";
            sb.append(escape(String.valueOf(i++))).append("\\. ")
              .append(escape(p.getSymbol())).append(" ")
              .append(escape(side)).append(" ")
              .append(escape(qty)).append(" shares @ \\$")
              .append(escape(entry));

            if (p.getUnrealizedPnl() != null) {
                double upnl = p.getUnrealizedPnl().doubleValue();
                String sign = upnl >= 0 ? "\\+" : "\\-";
                sb.append(" P&L: ").append(sign).append("\\$")
                  .append(escape(String.format(Locale.US, "%.2f", Math.abs(upnl))));
            }
            sb.append("\n");
        }

        sb.append(MessageFormatter.SEP).append("\n");
        sb.append("Total: ").append(escape(String.valueOf(positions.size()))).append(" position\\(s\\)");
        telegramClient.sendMessage(chatId, sb.toString());
    }

    private void handlePnl(String chatId) {
        try {
            DailySummaryService.DailySummaryData data = dailySummaryService.getDailySummary();
            String summary = messageFormatter.formatDailySummary(data);
            telegramClient.sendMessage(chatId, summary);
        } catch (Exception e) {
            log.error("Failed to fetch P&L data: {}", e.getMessage());
            telegramClient.sendMessage(chatId, "Failed to retrieve P&L data\\.");
        }
    }

    private void handlePause(String chatId, String reason) {
        tradingControlService.pause(reason.isEmpty() ? null : reason);
        String reasonText = reason.isEmpty() ? "" : ": " + escape(reason);
        telegramClient.sendMessage(chatId,
                "⏸️ Trading *PAUSED*" + reasonText + "\\. All new signals will be rejected\\.");
    }

    private void handleResume(String chatId) {
        tradingControlService.resume();
        telegramClient.sendMessage(chatId,
                "✅ Trading *RESUMED*\\. New signals will be processed\\.");
    }

    private void handleRisk(String chatId) {
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);
        long openCount = openPositions.size();

        double totalExposure = openPositions.stream()
                .filter(p -> p.getEntryPrice() != null && p.getQuantity() != null)
                .mapToDouble(p -> p.getEntryPrice().multiply(p.getQuantity()).doubleValue())
                .sum();

        double totalRisk = openPositions.stream()
                .filter(p -> p.getEntryPrice() != null && p.getStopLoss() != null && p.getQuantity() != null)
                .mapToDouble(p -> p.getEntryPrice().subtract(p.getStopLoss()).abs()
                        .multiply(p.getQuantity()).doubleValue())
                .sum();

        StringBuilder sb = new StringBuilder();
        sb.append("⚖️ *RISK DASHBOARD*\n");
        sb.append(MessageFormatter.SEP).append("\n");
        sb.append("Open Positions: ").append(escape(String.valueOf(openCount))).append("\n");
        sb.append("Total Exposure: \\$").append(escape(String.format(Locale.US, "%.2f", totalExposure))).append("\n");
        sb.append("Total Risk: \\$").append(escape(String.format(Locale.US, "%.2f", totalRisk))).append("\n");
        sb.append(MessageFormatter.SEP);

        telegramClient.sendMessage(chatId, sb.toString());
    }

    private void handleCancel(String chatId, String symbol) {
        if (symbol.isBlank()) {
            telegramClient.sendMessage(chatId, "Usage: /cancel \\[SYMBOL\\]");
            return;
        }
        String upperSymbol = symbol.toUpperCase().trim();
        List<Order> activeOrders = orderRepository.findBySymbolAndStatusIn(upperSymbol, List.of(
                Order.OrderStatus.PENDING,
                Order.OrderStatus.SUBMITTED,
                Order.OrderStatus.PARTIALLY_FILLED
        ));

        if (activeOrders.isEmpty()) {
            telegramClient.sendMessage(chatId,
                    "No active orders found for *" + escape(upperSymbol) + "*\\.");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        for (Order order : activeOrders) {
            if (order.getId() == null) {
                log.warn("Skipping cancel for order with null id: symbol={}", upperSymbol);
                failCount++;
                continue;
            }
            try {
                String url = orderExecutorUrl + "/api/orders/" + order.getId() + "/cancel";
                ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    successCount++;
                } else {
                    failCount++;
                    log.error("Failed to cancel order {}: {}", order.getId(), response.getBody());
                }
            } catch (Exception e) {
                failCount++;
                log.error("Error calling order-executor to cancel order {}", order.getId(), e);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🚫 *CANCEL ORDERS CONFIRMATION*\n");
        sb.append(MessageFormatter.SEP).append("\n");
        sb.append("Symbol: ").append(escape(upperSymbol)).append("\n");
        sb.append("Cancelled: ").append(escape(String.valueOf(successCount))).append("\n");
        if (failCount > 0) {
            sb.append("Failed: ").append(escape(String.valueOf(failCount))).append("\n");
        }
        sb.append(MessageFormatter.SEP);

        telegramClient.sendMessage(chatId, sb.toString());
    }

    private void handleHelp(String chatId) {
        String msg = "🤖 *TRADIE BOT COMMANDS*\n" +
                MessageFormatter.SEP + "\n" +
                "/status \\- System status and open positions count\n" +
                "/positions \\- List open positions with unrealized P&L\n" +
                "/pnl \\- Today's P&L summary\n" +
                "/pause \\[reason\\] \\- Pause signal processing\n" +
                "/resume \\- Resume signal processing\n" +
                "/risk \\- Portfolio risk overview\n" +
                "/cancel \\[symbol\\] \\- Cancel active orders for symbol\n" +
                "/help \\- Show this help message\n" +
                MessageFormatter.SEP;
        telegramClient.sendMessage(chatId, msg);
    }
}
