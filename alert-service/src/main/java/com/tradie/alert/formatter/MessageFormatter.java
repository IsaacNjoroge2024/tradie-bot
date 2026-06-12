package com.tradie.alert.formatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradie.alert.service.DailySummaryService.DailySummaryData;
import com.tradie.alert.util.TelegramEscaper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.tradie.alert.util.TelegramEscaper.escape;

/**
 * Formats Kafka alert events into MarkdownV2-escaped Telegram messages.
 * All dynamic values are escaped via {@link TelegramEscaper#escape(String)}.
 */
@Service
public class MessageFormatter {

    public static final String SEP = "━━━━━━━━━━━━━━━━";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.of("America/New_York"));

    /**
     * Routes an OrderEvent JsonNode to the correct formatter based on the {@code type} field.
     * Returns null for event types that should not trigger a Telegram notification.
     */
    public String formatOrderEvent(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "FILLED"                 -> formatTradeEntry(node);
            case "TAKE_PROFIT_HIT"        -> formatTradeExit(node, true, false);
            case "STOP_LOSS_HIT"          -> formatTradeExit(node, false, false);
            case "POSITION_CLOSED_MANUAL" -> formatTradeExit(node, null, true);
            default -> null;
        };
    }

    /**
     * Formats a RejectionEvent JsonNode as a Signal Rejected alert.
     */
    public String formatRejectionEvent(JsonNode node) {
        String symbol    = node.path("symbol").asText("UNKNOWN");
        String action    = node.path("action").asText("UNKNOWN");
        String reason    = node.path("rejectionReason").asText("Unknown reason");
        String strategy  = node.path("strategy").asText("");

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *SIGNAL REJECTED*\n");
        sb.append(SEP).append("\n");
        sb.append("Symbol: ").append(escape(symbol)).append("\n");
        sb.append("Action: ").append(escape(action)).append("\n");
        sb.append("Reason: ").append(escape(reason)).append("\n");
        if (!strategy.isBlank()) {
            sb.append("Strategy: ").append(escape(strategy)).append("\n");
        }
        sb.append(SEP);
        return sb.toString();
    }

    /**
     * Formats a system-level alert (connection issues, errors, etc.).
     */
    public String formatSystemAlert(String service, String type, String message) {
        return "🔧 *SYSTEM ALERT*\n" +
               SEP + "\n" +
               "Type: " + escape(type) + "\n" +
               "Service: " + escape(service) + "\n" +
               "Message: " + escape(message) + "\n" +
               SEP;
    }

    /**
     * Formats the daily P&L summary for the scheduled 5 PM EST report.
     */
    public String formatDailySummary(DailySummaryData data) {
        String pnlSign   = data.totalPnl()  >= 0 ? "\\+" : "\\-";
        String bestSign  = data.bestTrade() >= 0 ? "\\+" : "\\-";
        String worstSign = data.worstTrade() >= 0 ? "\\+" : "\\-";
        String pnlValue  = escape(String.format(Locale.US, "%.2f", Math.abs(data.totalPnl())));
        String winRate   = escape(String.format(Locale.US, "%.1f", data.winRate()));
        String bestStr   = escape(String.format(Locale.US, "%.2f", Math.abs(data.bestTrade())));
        String worstStr  = escape(String.format(Locale.US, "%.2f", Math.abs(data.worstTrade())));
        String openCount = escape(String.valueOf(data.openPositions()));
        String trades    = escape(String.valueOf(data.totalTrades()));
        String wins      = escape(String.valueOf(data.wins()));
        String losses    = escape(String.valueOf(data.losses()));

        return "📊 *DAILY SUMMARY*\n" +
               SEP + "\n" +
               "Total P&L: " + pnlSign + "$" + pnlValue + "\n" +
               "Total Trades: " + trades + "\n" +
               "Wins: " + wins + " \\| Losses: " + losses + "\n" +
               "Win Rate: " + winRate + "%\n" +
               "Best Trade: " + bestSign + "$" + bestStr + "\n" +
               "Worst Trade: " + worstSign + "$" + worstStr + "\n" +
               "Open Positions: " + openCount + "\n" +
               SEP;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String formatTradeEntry(JsonNode node) {
        String symbol   = node.path("symbol").asText("UNKNOWN");
        String side     = node.path("side").asText("UNKNOWN");
        double price    = node.path("price").asDouble(0);
        double qty      = node.path("quantity").asDouble(0);
        String strategy = node.path("strategy").asText("");
        Instant ts      = parseTimestamp(node);

        StringBuilder sb = new StringBuilder();
        sb.append("🟢 *TRADE ENTRY*\n");
        sb.append(SEP).append("\n");
        sb.append("Symbol: ").append(escape(symbol)).append("\n");
        sb.append("Side: ").append(escape(side)).append("\n");
        sb.append("Entry: $").append(escape(String.format(Locale.US, "%.2f", price))).append("\n");

        if (!node.path("stopLoss").isMissingNode() && !node.path("stopLoss").isNull()) {
            double sl    = node.path("stopLoss").asDouble();
            double slPct = price > 0 ? (sl - price) / price * 100 : 0;
            String slPctStr = escape(String.format(Locale.US, "%.1f", Math.abs(slPct)));
            String slPctSign = slPct < 0 ? "\\-" : "\\+";
            sb.append("Stop Loss: $").append(escape(String.format(Locale.US, "%.2f", sl)))
              .append(" \\(").append(slPctSign).append(slPctStr).append("%\\)\n");
        }

        if (!node.path("takeProfit").isMissingNode() && !node.path("takeProfit").isNull()) {
            double tp    = node.path("takeProfit").asDouble();
            double tpPct = price > 0 ? (tp - price) / price * 100 : 0;
            String tpPctStr  = escape(String.format(Locale.US, "%.1f", Math.abs(tpPct)));
            String tpPctSign = tpPct >= 0 ? "\\+" : "\\-";
            sb.append("Take Profit: $").append(escape(String.format(Locale.US, "%.2f", tp)))
              .append(" \\(").append(tpPctSign).append(tpPctStr).append("%\\)\n");
        }

        sb.append("Quantity: ").append(escape(formatQuantity(qty))).append("\n");
        if (!strategy.isBlank()) {
            sb.append("Strategy: ").append(escape(strategy)).append("\n");
        }
        sb.append(SEP).append("\n");
        sb.append("⏰ ").append(escape(TIMESTAMP_FMT.format(ts)));
        return sb.toString();
    }

    private String formatTradeExit(JsonNode node, Boolean isWin, boolean isManual) {
        String symbol   = node.path("symbol").asText("UNKNOWN");
        double exitPx   = node.path("price").asDouble(0);
        String strategy = node.path("strategy").asText("");
        Instant ts      = parseTimestamp(node);

        String resultLabel;
        if (isManual) {
            resultLabel = "🔄 MANUAL";
        } else if (Boolean.TRUE.equals(isWin)) {
            resultLabel = "✅ WIN";
        } else {
            resultLabel = "❌ LOSS";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔴 *TRADE CLOSED*\n");
        sb.append(SEP).append("\n");
        sb.append("Symbol: ").append(escape(symbol)).append("\n");
        sb.append("Result: ").append(resultLabel).append("\n");

        if (!node.path("entryPrice").isMissingNode() && !node.path("entryPrice").isNull()) {
            double entryPx = node.path("entryPrice").asDouble();
            double qty     = node.path("quantity").asDouble(0);
            String sideStr = node.path("side").asText("BUY");
            double pnl     = "SELL".equalsIgnoreCase(sideStr)
                    ? (entryPx - exitPx) * qty
                    : (exitPx - entryPx) * qty;
            String pnlSign  = pnl >= 0 ? "\\+" : "\\-";
            String pnlValue = escape(String.format(Locale.US, "%.2f", Math.abs(pnl)));

            sb.append("Entry: $").append(escape(String.format(Locale.US, "%.2f", entryPx))).append("\n");
            sb.append("Exit: $").append(escape(String.format(Locale.US, "%.2f", exitPx))).append("\n");
            sb.append("P&L: ").append(pnlSign).append("$").append(pnlValue).append("\n");
        } else {
            sb.append("Exit: $").append(escape(String.format(Locale.US, "%.2f", exitPx))).append("\n");
        }

        if (!strategy.isBlank()) {
            sb.append("Strategy: ").append(escape(strategy)).append("\n");
        }
        sb.append(SEP).append("\n");
        sb.append("⏰ ").append(escape(TIMESTAMP_FMT.format(ts)));
        return sb.toString();
    }

    private String formatQuantity(double qty) {
        if (qty == Math.floor(qty)) {
            return String.format(Locale.US, "%.0f", qty);
        }
        return String.format(Locale.US, "%.4f", qty);
    }

    private Instant parseTimestamp(JsonNode node) {
        try {
            String ts = node.path("timestamp").asText("");
            if (!ts.isBlank()) {
                return Instant.parse(ts);
            }
        } catch (Exception ignored) {}
        return Instant.now();
    }
}
