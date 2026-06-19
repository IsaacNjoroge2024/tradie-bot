package com.tradie.executor.journal;

import com.tradie.common.entity.TradeJournal;
import com.tradie.executor.dto.PerformanceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JournalAnalyticsServiceTest {

    private JournalAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new JournalAnalyticsService();
    }

    @Test
    void calculateStats_emptyList_returnsZeroStats() {
        PerformanceStats stats = service.calculateStats(List.of());

        assertThat(stats.totalTrades()).isEqualTo(0);
        assertThat(stats.wins()).isEqualTo(0);
        assertThat(stats.losses()).isEqualTo(0);
        assertThat(stats.winRate()).isEqualTo(0.0);
        assertThat(stats.profitFactor()).isEqualTo(0.0);
    }

    @Test
    void calculateStats_allWins_correctWinRate() {
        List<TradeJournal> entries = List.of(
                buildEntry("AAPL", "FVG", 100.0),
                buildEntry("AAPL", "FVG", 200.0),
                buildEntry("TSLA", "OB", 50.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.totalTrades()).isEqualTo(3);
        assertThat(stats.wins()).isEqualTo(3);
        assertThat(stats.losses()).isEqualTo(0);
        assertThat(stats.winRate()).isEqualTo(1.0);
        assertThat(stats.profitFactor()).isEqualTo(0.0); // no losses
    }

    @Test
    void calculateStats_mixedResults_correctMetrics() {
        List<TradeJournal> entries = List.of(
                buildEntry("AAPL", "FVG", 100.0),
                buildEntry("AAPL", "FVG", -50.0),
                buildEntry("TSLA", "OB", 200.0),
                buildEntry("TSLA", "OB", -100.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.totalTrades()).isEqualTo(4);
        assertThat(stats.wins()).isEqualTo(2);
        assertThat(stats.losses()).isEqualTo(2);
        assertThat(stats.winRate()).isEqualTo(0.5);
        assertThat(stats.avgWin()).isEqualTo(150.0);
        assertThat(stats.avgLoss()).isEqualTo(-75.0);
        assertThat(stats.profitFactor()).isEqualTo(2.0); // 300/150
        assertThat(stats.maxDrawdown()).isEqualTo(-100.0);
    }

    @Test
    void calculateStats_byStrategy_groupsCorrectly() {
        List<TradeJournal> entries = List.of(
                buildEntry("AAPL", "FVG", 100.0),
                buildEntry("TSLA", "FVG", -50.0),
                buildEntry("AAPL", "OB", 200.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.byStrategy()).containsKey("FVG");
        assertThat(stats.byStrategy()).containsKey("OB");
        assertThat(stats.byStrategy().get("FVG").trades()).isEqualTo(2);
        assertThat(stats.byStrategy().get("OB").trades()).isEqualTo(1);
    }

    @Test
    void calculateStats_bySymbol_sumsPnlPerSymbol() {
        List<TradeJournal> entries = List.of(
                buildEntry("AAPL", "FVG", 100.0),
                buildEntry("AAPL", "FVG", 50.0),
                buildEntry("TSLA", "OB", -30.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.bySymbol().get("AAPL")).isEqualTo(150.0);
        assertThat(stats.bySymbol().get("TSLA")).isEqualTo(-30.0);
    }

    @Test
    void calculateStats_cumulativeDrawdown_peakToTrough() {
        // +200, -50, -50 → equity: 200, 150, 100 → peak=200 → maxDrawdown=100-200=-100
        List<TradeJournal> entries = List.of(
                buildEntry("AAPL", "FVG", 200.0),
                buildEntry("AAPL", "FVG", -50.0),
                buildEntry("AAPL", "FVG", -50.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.maxDrawdown()).isEqualTo(-100.0);
    }

    @Test
    void calculateStats_entriesWithNullPnl_ignored() {
        TradeJournal open = new TradeJournal();
        open.setSymbol("AAPL");
        open.setRealizedPnl((BigDecimal) null); // open trade, no PnL yet

        List<TradeJournal> entries = List.of(open, buildEntry("AAPL", "FVG", 100.0));

        PerformanceStats stats = service.calculateStats(entries);

        assertThat(stats.totalTrades()).isEqualTo(1); // only closed trades counted
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private TradeJournal buildEntry(String symbol, String strategy, double pnl) {
        TradeJournal e = new TradeJournal();
        e.setSymbol(symbol);
        e.setStrategy(strategy);
        e.setRealizedPnl(BigDecimal.valueOf(pnl));
        e.setEntryTime(Instant.now());
        return e;
    }
}
