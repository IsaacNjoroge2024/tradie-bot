package com.tradie.executor.journal;

import com.tradie.common.entity.Order;
import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeJournal;
import com.tradie.common.repository.TradeJournalRepository;
import com.tradie.executor.dto.TradeJournalUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeJournalServiceTest {

    @Mock
    private TradeJournalRepository tradeJournalRepository;

    private TradeJournalService service;

    @BeforeEach
    void setUp() {
        service = new TradeJournalService(tradeJournalRepository);
        ReflectionTestUtils.setField(service, "autoCreate", true);
    }

    // ─── createEntry from Position ────────────────────────────────────────────

    @Test
    void createEntry_fromClosedPosition_savesJournalEntry() {
        Position position = buildClosedPosition();
        when(tradeJournalRepository.findByPositionId(position.getId())).thenReturn(Optional.empty());
        when(tradeJournalRepository.save(any(TradeJournal.class))).thenAnswer(inv -> inv.getArgument(0));

        TradeJournal result = service.createEntry(position);

        ArgumentCaptor<TradeJournal> captor = ArgumentCaptor.forClass(TradeJournal.class);
        verify(tradeJournalRepository).save(captor.capture());
        TradeJournal saved = captor.getValue();

        assertThat(saved.getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getSide()).isEqualTo("BUY");
        assertThat(saved.getEntryPrice()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(saved.getExitPrice()).isEqualByComparingTo(BigDecimal.valueOf(165.0));
        assertThat(saved.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(saved.getRealizedPnl()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(saved.getStrategy()).isEqualTo("FVG");
    }

    @Test
    void createEntry_alreadyExists_returnsExisting() {
        Position position = buildClosedPosition();
        TradeJournal existing = new TradeJournal();
        when(tradeJournalRepository.findByPositionId(position.getId())).thenReturn(Optional.of(existing));

        TradeJournal result = service.createEntry(position);

        verify(tradeJournalRepository, never()).save(any());
        assertThat(result).isSameAs(existing);
    }

    @Test
    void createEntry_autoCreateDisabled_returnsNull() {
        ReflectionTestUtils.setField(service, "autoCreate", false);
        Position position = buildClosedPosition();

        TradeJournal result = service.createEntry(position);

        assertThat(result).isNull();
        verifyNoInteractions(tradeJournalRepository);
    }

    // ─── updateEntry ─────────────────────────────────────────────────────────

    @Test
    void updateEntry_updatesAllFields() {
        UUID id = UUID.randomUUID();
        TradeJournal existing = new TradeJournal();
        when(tradeJournalRepository.findById(id)).thenReturn(Optional.of(existing));
        when(tradeJournalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeJournalUpdateRequest update = new TradeJournalUpdateRequest(
                "Great trade", "A", "GOOD", List.of("fvg", "trend"),
                "TRENDING", "Pre-FOMC setup", null, null);

        TradeJournal result = service.updateEntry(id, update);

        assertThat(result.getNotes()).isEqualTo("Great trade");
        assertThat(result.getSetupQuality()).isEqualTo("A");
        assertThat(result.getExecutionQuality()).isEqualTo("GOOD");
        assertThat(result.getTags()).containsExactly("fvg", "trend");
        assertThat(result.getMarketCondition()).isEqualTo("TRENDING");
        assertThat(result.getNewsContext()).isEqualTo("Pre-FOMC setup");
    }

    @Test
    void updateEntry_notFound_throwsIllegalArgument() {
        UUID id = UUID.randomUUID();
        when(tradeJournalRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEntry(id, new TradeJournalUpdateRequest(
                null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── exportToCsv ─────────────────────────────────────────────────────────

    @Test
    void exportToCsv_generatesHeaderAndRows() {
        TradeJournal entry = new TradeJournal();
        entry.setSymbol("AAPL");
        entry.setSide("BUY");
        entry.setEntryPrice(BigDecimal.valueOf(150.0));
        entry.setRealizedPnl(BigDecimal.valueOf(50.0));

        String csv = service.exportToCsv(List.of(entry));

        assertThat(csv).contains("id,symbol,side");
        assertThat(csv).contains("AAPL");
        assertThat(csv).contains("BUY");
    }

    @Test
    void exportToCsv_emptyList_returnsOnlyHeader() {
        String csv = service.exportToCsv(List.of());
        assertThat(csv).startsWith("id,symbol,side");
        long lines = csv.lines().count();
        assertThat(lines).isEqualTo(1);
    }

    @Test
    void exportToCsv_commasInNotesAreProperlyQuoted() {
        TradeJournal entry = new TradeJournal();
        entry.setSymbol("AAPL");
        entry.setNotes("Great trade, perfect entry");

        String csv = service.exportToCsv(List.of(entry));

        String dataRow = csv.lines().skip(1).findFirst().orElse("");
        assertThat(dataRow).contains("\"Great trade, perfect entry\"");
    }

    @Test
    void exportToCsv_formulaLeadingCharsAreNeutralized() {
        TradeJournal entry = new TradeJournal();
        entry.setSymbol("AAPL");
        entry.setNotes("=SUM(A1:A5)");

        String csv = service.exportToCsv(List.of(entry));

        String dataRow = csv.lines().skip(1).findFirst().orElse("");
        assertThat(dataRow).contains("'=SUM");
        assertThat(dataRow).doesNotContain("\"=SUM");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Position buildClosedPosition() {
        Position p = new Position();
        try {
            var idField = Position.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(p, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        p.setSymbol("AAPL");
        p.setSide(Order.OrderSide.BUY);
        p.setQuantity(BigDecimal.valueOf(10));
        p.setEntryPrice(BigDecimal.valueOf(150.0));
        p.setExitPrice(BigDecimal.valueOf(165.0));
        p.setRealizedPnl(BigDecimal.valueOf(150.0));
        p.setCommissionTotal(BigDecimal.valueOf(2.0));
        p.setStrategy("FVG");
        p.setOpenedAt(Instant.now().minusSeconds(3600));
        p.setClosedAt(Instant.now());
        p.setStatus(Position.PositionStatus.CLOSED);
        return p;
    }
}
