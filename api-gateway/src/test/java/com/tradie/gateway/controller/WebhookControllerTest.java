package com.tradie.gateway.controller;

import com.tradie.gateway.service.SignalIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@TestPropertySource(properties = "tradie.webhook.secret=test-secret")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignalIngestionService signalIngestionService;

    private static final String VALID_PAYLOAD = """
            {
                "symbol": "AAPL",
                "action": "BUY",
                "strategy": "FVG",
                "price": 150.00,
                "stop_loss": 148.00,
                "take_profit": 155.00,
                "auth_token": "test-secret"
            }
            """;

    @Test
    void receiveTradingViewSignal_validRequest_returnsOk() throws Exception {
        when(signalIngestionService.processIncomingSignal(any())).thenReturn("test-uuid-123");

        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("acknowledged"))
                .andExpect(jsonPath("$.signal_id").value("test-uuid-123"))
                .andExpect(jsonPath("$.message").value("Signal queued for processing"));
    }

    @Test
    void receiveTradingViewSignal_wrongAuthToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "action": "BUY",
                                    "strategy": "FVG",
                                    "price": 150.00,
                                    "auth_token": "wrong-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid authentication token"));
    }

    @Test
    void receiveTradingViewSignal_missingSymbol_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "action": "BUY",
                                    "strategy": "FVG",
                                    "price": 150.00,
                                    "auth_token": "test-secret"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveTradingViewSignal_invalidAction_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "action": "HOLD",
                                    "strategy": "FVG",
                                    "price": 150.00,
                                    "auth_token": "test-secret"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveTradingViewSignal_missingPrice_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "symbol": "AAPL",
                                    "action": "BUY",
                                    "strategy": "FVG",
                                    "auth_token": "test-secret"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiveTradingViewSignal_serviceException_returnsInternalServerError() throws Exception {
        when(signalIngestionService.processIncomingSignal(any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        mockMvc.perform(post("/api/v1/webhook/tradingview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to process signal"));
    }

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/webhook/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("api-gateway"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
