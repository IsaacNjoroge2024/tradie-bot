package com.tradie.executor.order;

import com.ib.client.Contract;
import com.tradie.common.entity.Order;
import com.tradie.executor.dto.OrderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractBuilderTest {

    private ContractBuilder contractBuilder;

    @BeforeEach
    void setUp() {
        contractBuilder = new ContractBuilder();
    }

    @Test
    void build_stock_returnsSMARTExchange() {
        OrderDTO order = buildOrderDTO("AAPL", "NASDAQ", "STK");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("AAPL");
        assertThat(contract.secType().getApiString()).isEqualTo("STK");
        assertThat(contract.exchange()).isEqualTo("SMART");
        assertThat(contract.currency()).isEqualTo("USD");
    }

    @Test
    void build_forex_returnsIDEALPRO() {
        OrderDTO order = buildOrderDTO("EUR", "IDEALPRO", "CASH");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("EUR");
        assertThat(contract.secType().getApiString()).isEqualTo("CASH");
        assertThat(contract.exchange()).isEqualTo("IDEALPRO");
        assertThat(contract.currency()).isEqualTo("USD");
    }

    @Test
    void build_forexAlias_returnsIDEALPRO() {
        OrderDTO order = buildOrderDTO("GBP", "IDEALPRO", "FOREX");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.secType().getApiString()).isEqualTo("CASH");
        assertThat(contract.exchange()).isEqualTo("IDEALPRO");
    }

    @Test
    void build_futures_usesOrderExchange() {
        OrderDTO order = buildOrderDTO("ES", "CME", "FUT");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("ES");
        assertThat(contract.secType().getApiString()).isEqualTo("FUT");
        assertThat(contract.exchange()).isEqualTo("CME");
    }

    @Test
    void build_crypto_returnsPAXOS() {
        OrderDTO order = buildOrderDTO("BTC", "PAXOS", "CRYPTO");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("BTC");
        assertThat(contract.secType().getApiString()).isEqualTo("CRYPTO");
        assertThat(contract.exchange()).isEqualTo("PAXOS");
    }

    @Test
    void build_unknownAssetClass_throwsException() {
        OrderDTO order = buildOrderDTO("XYZ", "NYSE", "UNKNOWN");

        assertThatThrownBy(() -> contractBuilder.build(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported assetClass: UNKNOWN");
    }

    @Test
    void build_assetClassIsCaseInsensitive() {
        OrderDTO order = buildOrderDTO("AAPL", "NASDAQ", "stk");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.secType().getApiString()).isEqualTo("STK");
        assertThat(contract.exchange()).isEqualTo("SMART");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private OrderDTO buildOrderDTO(String symbol, String exchange, String assetClass) {
        return new OrderDTO(
                UUID.randomUUID(), symbol, exchange, assetClass,
                Order.OrderSide.BUY, Order.OrderType.LIMIT,
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(140.00), BigDecimal.valueOf(165.00),
                "FVG", Instant.now().plusSeconds(300),
                BigDecimal.valueOf(300), 2.0, 4.0, 6.0,
                BigDecimal.valueOf(600), 2.0, "FIXED_FRACTIONAL"
        );
    }
}
