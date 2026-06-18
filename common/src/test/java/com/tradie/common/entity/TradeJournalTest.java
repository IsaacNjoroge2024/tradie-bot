package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeJournalTest {

    @Test
    void tradeJournal_settersAndGetters() {
        TradeJournal journal = new TradeJournal();
        Instant entryTime = Instant.now().minusSeconds(3600);
        Instant exitTime = Instant.now();

        journal.setSymbol("AAPL");
        journal.setSide("BUY");
        journal.setEntryTime(entryTime);
        journal.setExitTime(exitTime);
        journal.setEntryPrice(150.0);
        journal.setExitPrice(165.0);
        journal.setQuantity(10.0);
        journal.setRealizedPnl(150.0);
        journal.setRealizedPnlPct(10.0);
        journal.setCommissions(2.0);
        journal.setStrategy("FVG");
        journal.setTimeframe("1H");
        journal.setSetupQuality("A");
        journal.setExecutionQuality("GOOD");
        journal.setNotes("Clean FVG entry");
        journal.setTags(List.of("trend", "morning"));
        journal.setMarketCondition("TRENDING");

        assertThat(journal.getSymbol()).isEqualTo("AAPL");
        assertThat(journal.getSide()).isEqualTo("BUY");
        assertThat(journal.getEntryTime()).isEqualTo(entryTime);
        assertThat(journal.getExitTime()).isEqualTo(exitTime);
        assertThat(journal.getEntryPrice()).isEqualTo(150.0);
        assertThat(journal.getExitPrice()).isEqualTo(165.0);
        assertThat(journal.getQuantity()).isEqualTo(10.0);
        assertThat(journal.getRealizedPnl()).isEqualTo(150.0);
        assertThat(journal.getRealizedPnlPct()).isEqualTo(10.0);
        assertThat(journal.getCommissions()).isEqualTo(2.0);
        assertThat(journal.getStrategy()).isEqualTo("FVG");
        assertThat(journal.getTimeframe()).isEqualTo("1H");
        assertThat(journal.getSetupQuality()).isEqualTo("A");
        assertThat(journal.getExecutionQuality()).isEqualTo("GOOD");
        assertThat(journal.getNotes()).isEqualTo("Clean FVG entry");
        assertThat(journal.getTags()).containsExactly("trend", "morning");
        assertThat(journal.getMarketCondition()).isEqualTo("TRENDING");
    }

    @Test
    void tagsConverter_roundTrip() {
        TagsConverter converter = new TagsConverter();

        List<String> tags = List.of("fvg", "london", "ny");
        String dbValue = converter.convertToDatabaseColumn(tags);
        List<String> recovered = converter.convertToEntityAttribute(dbValue);

        assertThat(recovered).containsExactlyElementsOf(tags);
    }

    @Test
    void tagsConverter_nullInput_returnsNull() {
        TagsConverter converter = new TagsConverter();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void tagsConverter_emptyList_returnsNull() {
        TagsConverter converter = new TagsConverter();
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }
}
