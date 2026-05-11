package com.tradie.strategy.service;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationAdjusterTest {

    @Mock
    private PositionRepository positionRepository;

    private CorrelationAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new CorrelationAdjuster(positionRepository);
    }

    @Test
    void adjust_noOpenPositions_returnsOriginalQuantity() {
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("AAPL", BigDecimal.valueOf(40), adjustments);

        assertEquals(0, BigDecimal.valueOf(40).compareTo(result));
        assertTrue(adjustments.isEmpty());
    }

    @Test
    void adjust_highCorrelation_reducesByFiftyPercent() {
        // SPY ↔ QQQ: correlation = 0.85 (≥ 0.7 threshold → reduce by 50%)
        Position position = new Position();
        position.setSymbol("SPY");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("QQQ", BigDecimal.valueOf(100), adjustments);

        assertEquals(0, BigDecimal.valueOf(50).compareTo(result));
        assertEquals(1, adjustments.size());
        assertTrue(adjustments.get(0).contains("50%"));
    }

    @Test
    void adjust_mediumCorrelation_reducesByTwentyFivePercent() {
        // Unknown pair defaults to 0.5 correlation (0.3 ≤ ρ < 0.7 → reduce by 25%)
        Position position = new Position();
        position.setSymbol("GOOG");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("AMZN", BigDecimal.valueOf(100), adjustments);

        assertEquals(0, BigDecimal.valueOf(75).compareTo(result));
        assertEquals(1, adjustments.size());
        assertTrue(adjustments.get(0).contains("25%"));
    }

    @Test
    void adjust_sameSymbol_reducesByFiftyPercent() {
        // Same symbol → correlation = 1.0 → reduce by 50%
        Position position = new Position();
        position.setSymbol("AAPL");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("AAPL", BigDecimal.valueOf(100), adjustments);

        assertEquals(0, BigDecimal.valueOf(50).compareTo(result));
    }

    @Test
    void adjust_knownMediumHighPair_aapl_msft_reducesByFiftyPercent() {
        // AAPL ↔ MSFT: correlation = 0.75 (≥ 0.7 → reduce by 50%)
        Position position = new Position();
        position.setSymbol("AAPL");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("MSFT", BigDecimal.valueOf(100), adjustments);

        assertEquals(0, BigDecimal.valueOf(50).compareTo(result));
    }

    @Test
    void adjust_btcEthPair_reducesByFiftyPercent() {
        // BTC ↔ ETH: correlation = 0.85 (≥ 0.7 → reduce by 50%)
        Position position = new Position();
        position.setSymbol("BTC");
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        List<String> adjustments = new ArrayList<>();
        BigDecimal result = adjuster.adjust("ETH", BigDecimal.valueOf(100), adjustments);

        assertEquals(0, BigDecimal.valueOf(50).compareTo(result));
    }
}
