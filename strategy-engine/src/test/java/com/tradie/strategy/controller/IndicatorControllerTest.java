package com.tradie.strategy.controller;

import com.tradie.common.entity.TradeSignal;
import com.tradie.strategy.dto.ConfirmationResult;
import com.tradie.strategy.dto.IndicatorSnapshot;
import com.tradie.strategy.service.IndicatorCalculatorService;
import com.tradie.strategy.service.OHLCVDataService;
import com.tradie.strategy.service.SignalConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ta4j.core.BaseBarSeries;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IndicatorController.class)
class IndicatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OHLCVDataService ohlcvDataService;

    @MockBean
    private IndicatorCalculatorService indicatorCalculatorService;

    @MockBean
    private SignalConfirmationService signalConfirmationService;

    private IndicatorSnapshot sampleSnapshot(String symbol) {
        return new IndicatorSnapshot(
                symbol, "1H", Instant.now(),
                150.0, 149.0, 151.0, 148.0,
                148.0, 147.0, 145.0, 140.0, "BULLISH",
                55.0, "NEUTRAL",
                0.5, 0.3, 0.2,
                28.0, 22.0, 18.0, "RANGING",
                1.5, 155.0, 150.0, 145.0, 10.0,
                1000.0, 900.0, 1.11
        );
    }

    private BaseBarSeries nonEmptySeries() {
        BaseBarSeries series = new BaseBarSeries("TEST");
        series.addBar(java.time.Duration.ofHours(1), java.time.ZonedDateTime.now(),
                100.0, 102.0, 99.0, 101.0, 1000.0);
        return series;
    }

    @Test
    void getIndicators_returnsSnapshot() throws Exception {
        when(ohlcvDataService.getBarSeries(eq("AAPL"), anyString(), anyString()))
                .thenReturn(nonEmptySeries());
        when(indicatorCalculatorService.calculateLatest(any(), eq("AAPL"), anyString(), anyString()))
                .thenReturn(sampleSnapshot("AAPL"));

        mockMvc.perform(get("/api/indicators/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.timeframe").value("1H"))
                .andExpect(jsonPath("$.close").value(150.0))
                .andExpect(jsonPath("$.rsi").value(55.0))
                .andExpect(jsonPath("$.emaAlignment").value("BULLISH"));
    }

    @Test
    void getIndicators_noData_returns204() throws Exception {
        when(ohlcvDataService.getBarSeries(any(), any(), any()))
                .thenReturn(new BaseBarSeries("EMPTY"));

        mockMvc.perform(get("/api/indicators/UNKNOWN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getIndicators_withTimeframeParam_passesThrough() throws Exception {
        when(ohlcvDataService.getBarSeries(eq("AAPL"), eq("NYSE"), eq("15M")))
                .thenReturn(nonEmptySeries());
        when(indicatorCalculatorService.calculateLatest(any(), eq("AAPL"), eq("NYSE"), eq("15M")))
                .thenReturn(sampleSnapshot("AAPL"));

        mockMvc.perform(get("/api/indicators/AAPL")
                        .param("exchange", "NYSE")
                        .param("timeframe", "15M"))
                .andExpect(status().isOk());
    }

    @Test
    void getIndicatorHistory_returnsHistoryList() throws Exception {
        when(ohlcvDataService.getBarSeries(any(), any(), any())).thenReturn(nonEmptySeries());
        when(indicatorCalculatorService.calculateHistory(any(), eq("AAPL"), anyString(), eq(10)))
                .thenReturn(List.of(sampleSnapshot("AAPL")));

        mockMvc.perform(get("/api/indicators/AAPL/history").param("periods", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getIndicatorHistory_noData_returns204() throws Exception {
        when(ohlcvDataService.getBarSeries(any(), any(), any()))
                .thenReturn(new BaseBarSeries("EMPTY"));

        mockMvc.perform(get("/api/indicators/AAPL/history"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getIndicatorHistory_invalidPeriods_returns400() throws Exception {
        mockMvc.perform(get("/api/indicators/AAPL/history").param("periods", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getIndicatorHistory_negativePeriods_returns400() throws Exception {
        mockMvc.perform(get("/api/indicators/AAPL/history").param("periods", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmSignal_buy_returnsConfirmationResult() throws Exception {
        when(signalConfirmationService.confirm(any()))
                .thenReturn(new ConfirmationResult(true, 3, 4, 80.0,
                        List.of("RSI not overbought", "Price above EMA50", "ADX trending"),
                        List.of("MACD below signal")));

        mockMvc.perform(get("/api/indicators/AAPL/confirm").param("action", "BUY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.confirmationCount").value(3))
                .andExpect(jsonPath("$.adjustedConfidence").value(80.0));
    }

    @Test
    void confirmSignal_invalidAction_returns400() throws Exception {
        mockMvc.perform(get("/api/indicators/AAPL/confirm").param("action", "INVALID"))
                .andExpect(status().isBadRequest());
    }
}
