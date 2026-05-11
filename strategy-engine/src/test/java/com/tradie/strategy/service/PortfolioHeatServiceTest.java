package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.dto.AccountInfo;
import com.tradie.strategy.dto.PortfolioHeatData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioHeatServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;

    @Mock
    private AccountService accountService;

    private PortfolioHeatService service;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AccountInfo defaultAccount = new AccountInfo(
            BigDecimal.valueOf(10000), BigDecimal.valueOf(10000),
            BigDecimal.valueOf(10000), BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        service = new PortfolioHeatService(
                positionRepository, redisTemplate, objectMapper, accountService);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void calculateAndStore_noOpenPositions_returnsZeroHeat() {
        when(accountService.getAccountInfo()).thenReturn(defaultAccount);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        PortfolioHeatData result = service.calculateAndStore();

        assertEquals(0.0, result.totalHeatPct());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void calculateAndStore_withOpenPosition_calculatesHeat() {
        // entry=100, stop=95, distance=5, qty=40 → risk=200 → 200/10000*100=2%
        Position position = new Position();
        position.setSymbol("AAPL");
        position.setEntryPrice(BigDecimal.valueOf(100));
        position.setStopLoss(BigDecimal.valueOf(95));
        position.setQuantity(BigDecimal.valueOf(40));

        when(accountService.getAccountInfo()).thenReturn(defaultAccount);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        PortfolioHeatData result = service.calculateAndStore();

        assertEquals(2.0, result.totalHeatPct(), 0.01);
        assertEquals(1, result.positions().size());
        assertEquals("AAPL", result.positions().get(0).symbol());
    }

    @Test
    void getCurrentHeatPct_cachedValue_returnsCached() throws Exception {
        PortfolioHeatData cached = new PortfolioHeatData(3.5, List.of(), Instant.now());
        when(valueOps.get("portfolio:heat")).thenReturn(objectMapper.writeValueAsString(cached));

        double heat = service.getCurrentHeatPct();

        assertEquals(3.5, heat, 0.001);
        verify(positionRepository, never()).findByStatus(any());
    }

    @Test
    void getCurrentHeatPct_noCache_calculatesFromDb() {
        when(accountService.getAccountInfo()).thenReturn(defaultAccount);
        when(valueOps.get("portfolio:heat")).thenReturn(null);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        double heat = service.getCurrentHeatPct();

        assertEquals(0.0, heat);
        verify(positionRepository).findByStatus(Position.PositionStatus.OPEN);
    }

    @Test
    void calculateAndStore_positionWithNoStopLoss_skipped() {
        Position position = new Position();
        position.setSymbol("AAPL");
        position.setEntryPrice(BigDecimal.valueOf(100));
        position.setStopLoss(null);
        position.setQuantity(BigDecimal.valueOf(40));

        when(accountService.getAccountInfo()).thenReturn(defaultAccount);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of(position));

        PortfolioHeatData result = service.calculateAndStore();

        assertEquals(0.0, result.totalHeatPct());
        assertTrue(result.positions().isEmpty());
    }

    @Test
    void calculateAndStore_cachesResultInRedis() {
        when(accountService.getAccountInfo()).thenReturn(defaultAccount);
        when(positionRepository.findByStatus(Position.PositionStatus.OPEN)).thenReturn(List.of());

        service.calculateAndStore();

        verify(valueOps).set(eq("portfolio:heat"), anyString(), any(Duration.class));
    }
}
