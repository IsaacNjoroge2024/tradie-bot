package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TradeSignalTest {

    @Test
    void defaultStatusIsPending() {
        TradeSignal signal = new TradeSignal();
        assertEquals(TradeSignal.SignalStatus.PENDING, signal.getStatus());
    }

    @Test
    void createdAtIsNullBeforePersist() {
        TradeSignal signal = new TradeSignal();
        assertNull(signal.getCreatedAt());
    }

    @Test
    void onCreateSetsCreatedAt() {
        TradeSignal signal = new TradeSignal();
        Instant before = Instant.now();
        signal.onCreate();
        Instant after = Instant.now();

        assertNotNull(signal.getCreatedAt());
        assertFalse(signal.getCreatedAt().isBefore(before));
        assertFalse(signal.getCreatedAt().isAfter(after));
    }

    @Test
    void onCreateDoesNotOverwriteExistingCreatedAt() {
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
        TradeSignal signal = new TradeSignal();
        signal.setCreatedAt(fixed);
        signal.onCreate();

        assertEquals(fixed, signal.getCreatedAt());
    }

    @Test
    void fieldAssignmentIsCorrect() {
        TradeSignal signal = new TradeSignal();
        signal.setSymbol("AAPL");
        signal.setExchange("NASDAQ");
        signal.setAction(TradeSignal.SignalAction.BUY);
        signal.setStrategy("FVG");
        signal.setSource(TradeSignal.SignalSource.TRADINGVIEW);
        signal.setPrice(new BigDecimal("150.50"));
        signal.setStopLoss(new BigDecimal("148.00"));
        signal.setTakeProfit(new BigDecimal("155.00"));
        signal.setConfidenceScore(0.85);
        signal.setTimeframe("1h");

        assertEquals("AAPL", signal.getSymbol());
        assertEquals("NASDAQ", signal.getExchange());
        assertEquals(TradeSignal.SignalAction.BUY, signal.getAction());
        assertEquals("FVG", signal.getStrategy());
        assertEquals(TradeSignal.SignalSource.TRADINGVIEW, signal.getSource());
        assertEquals(new BigDecimal("150.50"), signal.getPrice());
        assertEquals(new BigDecimal("148.00"), signal.getStopLoss());
        assertEquals(new BigDecimal("155.00"), signal.getTakeProfit());
        assertEquals(0.85, signal.getConfidenceScore());
        assertEquals("1h", signal.getTimeframe());
    }

    @Test
    void statusTransitionIsCorrect() {
        TradeSignal signal = new TradeSignal();
        signal.setStatus(TradeSignal.SignalStatus.VALIDATED);
        assertEquals(TradeSignal.SignalStatus.VALIDATED, signal.getStatus());

        signal.setStatus(TradeSignal.SignalStatus.EXECUTED);
        assertEquals(TradeSignal.SignalStatus.EXECUTED, signal.getStatus());

        signal.setStatus(TradeSignal.SignalStatus.REJECTED);
        signal.setRejectionReason("Outside kill zone");
        assertEquals(TradeSignal.SignalStatus.REJECTED, signal.getStatus());
        assertEquals("Outside kill zone", signal.getRejectionReason());
    }

    @Test
    void signalActionEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> TradeSignal.SignalAction.valueOf("BUY"));
        assertDoesNotThrow(() -> TradeSignal.SignalAction.valueOf("SELL"));
        assertEquals(2, TradeSignal.SignalAction.values().length);
    }

    @Test
    void signalSourceEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> TradeSignal.SignalSource.valueOf("TRADINGVIEW"));
        assertDoesNotThrow(() -> TradeSignal.SignalSource.valueOf("INTERNAL"));
        assertDoesNotThrow(() -> TradeSignal.SignalSource.valueOf("MANUAL"));
        assertEquals(3, TradeSignal.SignalSource.values().length);
    }

    @Test
    void signalStatusEnumValuesAreCorrect() {
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("PENDING"));
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("VALIDATED"));
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("EXECUTED"));
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("REJECTED"));
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("EXPIRED"));
        assertDoesNotThrow(() -> TradeSignal.SignalStatus.valueOf("PUBLISH_FAILED"));
        assertEquals(6, TradeSignal.SignalStatus.values().length);
    }

    @Test
    void processedAndExecutedTimestampsAreNullByDefault() {
        TradeSignal signal = new TradeSignal();
        assertNull(signal.getProcessedAt());
        assertNull(signal.getExecutedAt());
    }
}
