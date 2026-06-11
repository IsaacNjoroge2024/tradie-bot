package com.tradie.alert.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradie.alert.service.DailySummaryService.DailySummaryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageFormatterTest {

    private MessageFormatter formatter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        formatter    = new MessageFormatter();
        objectMapper = new ObjectMapper();
    }

    // ─── OrderEvent: FILLED ───────────────────────────────────────────────────

    @Test
    void formatOrderEvent_filled_containsTradeEntryHeader() {
        ObjectNode node = buildOrderEvent("FILLED", "AAPL", "BUY", 150.50, 100.0, "FVG_BULLISH");
        node.put("stopLoss", 147.00);
        node.put("takeProfit", 157.50);

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("TRADE ENTRY"));
        assertTrue(result.contains("AAPL"));
        assertTrue(result.contains("BUY"));
        assertTrue(result.contains("150"));
    }

    @Test
    void formatOrderEvent_filled_withStopAndTakeProfit_includesBoth() {
        ObjectNode node = buildOrderEvent("FILLED", "TSLA", "SELL", 250.00, 50.0, "OB");
        node.put("stopLoss", 255.00);
        node.put("takeProfit", 240.00);

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("Stop Loss"));
        assertTrue(result.contains("Take Profit"));
    }

    @Test
    void formatOrderEvent_filled_withoutStopTakeProfit_doesNotShowThem() {
        ObjectNode node = buildOrderEvent("FILLED", "AAPL", "BUY", 150.00, 10.0, "FVG");

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertFalse(result.contains("Stop Loss"));
        assertFalse(result.contains("Take Profit"));
    }

    // ─── OrderEvent: exits ────────────────────────────────────────────────────

    @Test
    void formatOrderEvent_takeProfitHit_showsWin() {
        ObjectNode node = buildOrderEvent("TAKE_PROFIT_HIT", "AAPL", "BUY", 165.00, 100.0, "FVG");
        node.put("entryPrice", 150.00);

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("TRADE CLOSED"));
        assertTrue(result.contains("WIN"));
        assertTrue(result.contains("P&L"));
    }

    @Test
    void formatOrderEvent_stopLossHit_showsLoss() {
        ObjectNode node = buildOrderEvent("STOP_LOSS_HIT", "AAPL", "BUY", 140.00, 100.0, "FVG");
        node.put("entryPrice", 150.00);

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("LOSS"));
    }

    @Test
    void formatOrderEvent_manualClose_showsManualResult() {
        ObjectNode node = buildOrderEvent("POSITION_CLOSED_MANUAL", "AAPL", "BUY", 153.00, 100.0, "FVG");
        node.put("entryPrice", 150.00);

        String result = formatter.formatOrderEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("MANUAL"));
    }

    @Test
    void formatOrderEvent_unknownType_returnsNull() {
        ObjectNode node = buildOrderEvent("SUBMITTED", "AAPL", "BUY", 150.00, 10.0, "FVG");

        assertNull(formatter.formatOrderEvent(node));
    }

    // ─── RejectionEvent ───────────────────────────────────────────────────────

    @Test
    void formatRejectionEvent_containsSignalRejectedHeader() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("symbol", "TSLA");
        node.put("action", "BUY");
        node.put("rejectionReason", "High-impact FOMC event imminent");
        node.put("strategy", "FVG");

        String result = formatter.formatRejectionEvent(node);

        assertNotNull(result);
        assertTrue(result.contains("SIGNAL REJECTED"));
        assertTrue(result.contains("TSLA"));
        assertTrue(result.contains("FOMC"));
    }

    // ─── System alert ─────────────────────────────────────────────────────────

    @Test
    void formatSystemAlert_containsTypeAndService() {
        String result = formatter.formatSystemAlert("IBKR", "CONNECTION_LOST",
                "Connection to TWS lost");

        // escape() converts _ to \_, so check for the parts that survive escaping
        assertTrue(result.contains("SYSTEM ALERT"));
        assertTrue(result.contains("CONNECTION"));
        assertTrue(result.contains("IBKR"));
    }

    // ─── Daily summary ────────────────────────────────────────────────────────

    @Test
    void formatDailySummary_positivePnl_showsPlusSign() {
        DailySummaryData data = new DailySummaryData(1250.0, 5, 4, 1, 80.0, 500.0, -250.0, 2);
        String result = formatter.formatDailySummary(data);

        assertTrue(result.contains("DAILY SUMMARY"));
        assertTrue(result.contains("1250"));
    }

    @Test
    void formatDailySummary_negativePnl_showsMinusSign() {
        DailySummaryData data = new DailySummaryData(-300.0, 3, 1, 2, 33.3, 200.0, -500.0, 0);
        String result = formatter.formatDailySummary(data);

        assertTrue(result.contains("300"));
    }

    @Test
    void formatDailySummary_allLossDay_bestTradeShowsMinusNotPlus() {
        // bestTrade = -50 (smallest loss), worstTrade = -500 (largest loss)
        DailySummaryData data = new DailySummaryData(-550.0, 2, 0, 2, 0.0, -50.0, -500.0, 0);
        String result = formatter.formatDailySummary(data);

        assertFalse(result.contains("Best Trade: \\+"));
        assertTrue(result.contains("\\-$50"));
        assertTrue(result.contains("\\-$500"));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ObjectNode buildOrderEvent(String type, String symbol, String side,
                                       double price, double qty, String strategy) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("symbol", symbol);
        node.put("side", side);
        node.put("price", price);
        node.put("quantity", qty);
        node.put("strategy", strategy);
        node.put("timestamp", "2025-01-15T15:32:15Z");
        return node;
    }
}
