package com.tradie.strategy.service;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.OHLCVId;
import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.client.RegimeClient;
import com.tradie.strategy.dto.RegimeRecommendationDTO;
import com.tradie.strategy.dto.RegimeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegimeAwareValidatorTest {

    @Mock
    private RegimeClient regimeClient;

    @Mock
    private OHLCVDataService ohlcvDataService;

    private RegimeAwareValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RegimeAwareValidator(regimeClient, ohlcvDataService);
        ReflectionTestUtils.setField(validator, "minBars", 50);
        ReflectionTestUtils.setField(validator, "defaultTimeframe", "1H");
    }

    private TradeSignal signal(String strategy) {
        TradeSignal s = new TradeSignal();
        s.setSymbol("AAPL");
        s.setExchange("NASDAQ");
        s.setStrategy(strategy);
        s.setTimeframe("1H");
        return s;
    }

    private List<OHLCVCandle> candles(int count) {
        List<OHLCVCandle> list = new ArrayList<>();
        Instant base = Instant.now().minus(count, ChronoUnit.HOURS);
        for (int i = 0; i < count; i++) {
            OHLCVCandle c = new OHLCVCandle();
            c.setId(new OHLCVId(base.plus(i, ChronoUnit.HOURS), "AAPL", "NASDAQ", "1H"));
            c.setOpen(100 + i);
            c.setHigh(101 + i);
            c.setLow(99 + i);
            c.setClose(100.5 + i);
            c.setVolume(1000);
            list.add(c);
        }
        return list;
    }

    private RegimeResponse response(String regime, double sizeMultiplier, List<String> avoidStrategies) {
        RegimeRecommendationDTO rec = new RegimeRecommendationDTO(
                sizeMultiplier, List.of("TREND_FOLLOWING"), avoidStrategies, 1.0, 1.0, 4, "notes");
        return new RegimeResponse("AAPL", "1H", regime, 0.9, 5, Map.of(), rec);
    }

    @Test
    void validate_insufficientBars_throws() {
        when(ohlcvDataService.getRecentCandles("AAPL", "NASDAQ", "1H")).thenReturn(candles(10));

        assertThrows(IllegalStateException.class, () -> validator.validate(signal("FVG")));
    }

    @Test
    void validate_strategyNotInAvoidList_approvedWithSizeMultiplier() {
        when(ohlcvDataService.getRecentCandles("AAPL", "NASDAQ", "1H")).thenReturn(candles(60));
        when(regimeClient.detectRegime(eq("AAPL"), eq("1H"), anyList()))
                .thenReturn(response("trending_up", 1.0, List.of("MEAN_REVERSION")));

        RegimeAwareValidator.RegimeValidationResult result = validator.validate(signal("FVG"));

        assertTrue(result.approved());
        assertEquals("trending_up", result.regime());
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(result.sizeMultiplier()));
    }

    @Test
    void validate_strategyInAvoidList_rejected() {
        when(ohlcvDataService.getRecentCandles("AAPL", "NASDAQ", "1H")).thenReturn(candles(60));
        when(regimeClient.detectRegime(eq("AAPL"), eq("1H"), anyList()))
                .thenReturn(response("ranging", 0.8, List.of("FVG", "BREAKOUT")));

        RegimeAwareValidator.RegimeValidationResult result = validator.validate(signal("FVG"));

        assertFalse(result.approved());
        assertTrue(result.rejectionReason().contains("FVG"));
        assertTrue(result.rejectionReason().contains("ranging"));
    }

    @Test
    void validate_volatileRegime_returnsReducedSizeMultiplier() {
        when(ohlcvDataService.getRecentCandles("AAPL", "NASDAQ", "1H")).thenReturn(candles(60));
        when(regimeClient.detectRegime(eq("AAPL"), eq("1H"), anyList()))
                .thenReturn(response("volatile", 0.5, List.of("SCALPING")));

        RegimeAwareValidator.RegimeValidationResult result = validator.validate(signal("FVG"));

        assertTrue(result.approved());
        assertEquals(0, BigDecimal.valueOf(0.5).compareTo(result.sizeMultiplier()));
    }

    @Test
    void validate_nullTimeframe_fallsBackToDefault() {
        TradeSignal s = signal("FVG");
        s.setTimeframe(null);
        when(ohlcvDataService.getRecentCandles("AAPL", "NASDAQ", "1H")).thenReturn(candles(60));
        when(regimeClient.detectRegime(eq("AAPL"), eq("1H"), anyList()))
                .thenReturn(response("trending_up", 1.0, List.of()));

        RegimeAwareValidator.RegimeValidationResult result = validator.validate(s);

        assertTrue(result.approved());
    }
}
