package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        journal.setEntryPrice(BigDecimal.valueOf(150.0));
        journal.setExitPrice(BigDecimal.valueOf(165.0));
        journal.setQuantity(BigDecimal.valueOf(10.0));
        journal.setRealizedPnl(BigDecimal.valueOf(150.0));
        journal.setRealizedPnlPct(BigDecimal.valueOf(10.0));
        journal.setCommissions(BigDecimal.valueOf(2.0));
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
        assertThat(journal.getEntryPrice()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(journal.getExitPrice()).isEqualByComparingTo(BigDecimal.valueOf(165.0));
        assertThat(journal.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(journal.getRealizedPnl()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(journal.getRealizedPnlPct()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(journal.getCommissions()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
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

    @Test
    void tagsConverter_tagWithComma_roundTripsCorrectly() {
        TagsConverter converter = new TagsConverter();

        List<String> tags = List.of("ny,open", "fvg");
        String dbValue = converter.convertToDatabaseColumn(tags);
        List<String> recovered = converter.convertToEntityAttribute(dbValue);

        assertThat(recovered).containsExactly("ny,open", "fvg");
    }
}
