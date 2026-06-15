package com.tradie.alert.service;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailySummaryServiceTest {

    @Mock
    private PositionRepository positionRepository;

    private DailySummaryService service;

    @BeforeEach
    void setUp() {
        service = new DailySummaryService(positionRepository);
    }

    @Test
    void getDailySummary_noTrades_returnsZeros() {
        when(positionRepository.findByStatusAndClosedAtGreaterThanEqual(eq(Position.PositionStatus.CLOSED), any()))
                .thenReturn(List.of());
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        DailySummaryService.DailySummaryData data = service.getDailySummary();

        assertEquals(0, data.totalTrades());
        assertEquals(0, data.totalPnl().compareTo(BigDecimal.ZERO));
        assertEquals(0.0, data.winRate());
        assertEquals(0, data.openPositions());
    }

    @Test
    void getDailySummary_withWinsAndLosses_computesCorrectly() {
        Position win  = buildClosed(500.0);
        Position win2 = buildClosed(300.0);
        Position loss = buildClosed(-200.0);

        when(positionRepository.findByStatusAndClosedAtGreaterThanEqual(eq(Position.PositionStatus.CLOSED), any()))
                .thenReturn(List.of(win, win2, loss));
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(1L);

        DailySummaryService.DailySummaryData data = service.getDailySummary();

        assertEquals(3, data.totalTrades());
        assertEquals(2, data.wins());
        assertEquals(1, data.losses());
        assertEquals(0, data.totalPnl().compareTo(BigDecimal.valueOf(600.0)));
        assertEquals(0, data.bestTrade().compareTo(BigDecimal.valueOf(500.0)));
        assertEquals(0, data.worstTrade().compareTo(BigDecimal.valueOf(-200.0)));
        assertEquals(1, data.openPositions());
        assertEquals(66.67, data.winRate(), 0.1);
    }

    @Test
    void getDailySummary_breakEvenTrade_countedInTotalTrades() {
        Position win       = buildClosed(200.0);
        Position breakEven = buildClosed(0.0);
        Position loss      = buildClosed(-100.0);

        when(positionRepository.findByStatusAndClosedAtGreaterThanEqual(eq(Position.PositionStatus.CLOSED), any()))
                .thenReturn(List.of(win, breakEven, loss));
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        DailySummaryService.DailySummaryData data = service.getDailySummary();

        assertEquals(3, data.totalTrades());
        assertEquals(2, data.wins());    // break-even (pnl=0) counts as a win
        assertEquals(1, data.losses());
        assertEquals(0, data.totalPnl().compareTo(BigDecimal.valueOf(100.0)));
    }

    @Test
    void getDailySummary_positionWithNullPnl_isSkipped() {
        Position withPnl    = buildClosed(100.0);
        Position withoutPnl = new Position();
        withoutPnl.setStatus(Position.PositionStatus.CLOSED);

        when(positionRepository.findByStatusAndClosedAtGreaterThanEqual(eq(Position.PositionStatus.CLOSED), any()))
                .thenReturn(List.of(withPnl, withoutPnl));
        when(positionRepository.countByStatus(Position.PositionStatus.OPEN)).thenReturn(0L);

        DailySummaryService.DailySummaryData data = service.getDailySummary();

        assertEquals(1, data.totalTrades());
        assertEquals(0, data.totalPnl().compareTo(BigDecimal.valueOf(100.0)));
    }

    private Position buildClosed(double pnl) {
        Position p = new Position();
        p.setStatus(Position.PositionStatus.CLOSED);
        p.setRealizedPnl(BigDecimal.valueOf(pnl));
        p.setClosedAt(Instant.now());
        return p;
    }
}
