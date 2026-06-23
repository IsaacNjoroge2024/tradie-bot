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

    @Test
    void build_forex_sixCharSymbol_splitsCurrencies() {
        OrderDTO order = buildOrderDTO("EURUSD", "IDEALPRO", "CASH");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("EUR");
        assertThat(contract.currency()).isEqualTo("USD");
        assertThat(contract.secType().getApiString()).isEqualTo("CASH");
        assertThat(contract.exchange()).isEqualTo("IDEALPRO");
    }

    @Test
    void build_forex_slashSeparatedPair_splitsCurrencies() {
        OrderDTO order = buildOrderDTO("EUR/USD", "IDEALPRO", "FOREX");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("EUR");
        assertThat(contract.currency()).isEqualTo("USD");
    }

    @Test
    void build_forex_jpyPair_setsJpyQuote() {
        OrderDTO order = buildOrderDTO("USDJPY", "IDEALPRO", "CASH");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("USD");
        assertThat(contract.currency()).isEqualTo("JPY");
        assertThat(contract.exchange()).isEqualTo("IDEALPRO");
    }

    @Test
    void build_forex_dotFormatPair_splitsCurrencies() {
        OrderDTO order = buildOrderDTO("EUR.USD", "IDEALPRO", "CASH");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("EUR");
        assertThat(contract.currency()).isEqualTo("USD");
        assertThat(contract.secType().getApiString()).isEqualTo("CASH");
        assertThat(contract.exchange()).isEqualTo("IDEALPRO");
    }

    @Test
    void build_forex_malformedSevenCharSymbol_throwsException() {
        OrderDTO order = buildOrderDTO("EURUSDX", "IDEALPRO", "CASH");

        assertThatThrownBy(() -> contractBuilder.build(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid forex symbol format");
    }

    @Test
    void build_forex_slashWithFourCharQuote_throwsException() {
        OrderDTO order = buildOrderDTO("EUR/USDD", "IDEALPRO", "CASH");

        assertThatThrownBy(() -> contractBuilder.build(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid forex currencies");
    }

    @Test
    void build_forex_nullSymbol_throwsException() {
        OrderDTO order = buildOrderDTO(null, "IDEALPRO", "CASH");

        assertThatThrownBy(() -> contractBuilder.build(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forex symbol is required");
    }

    @Test
    void build_forex_blankSymbol_throwsException() {
        OrderDTO order = buildOrderDTO("   ", "IDEALPRO", "CASH");

        assertThatThrownBy(() -> contractBuilder.build(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forex symbol is required");
    }

    // ─── Futures ──────────────────────────────────────────────────────────────

    @Test
    void build_futures_withContractMonth_setsLastTradeDateAndLocalSymbol() {
        OrderDTO order = buildOrderDTO("ES", "CME", "FUT", "202506");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("ES");
        assertThat(contract.secType().getApiString()).isEqualTo("FUT");
        assertThat(contract.exchange()).isEqualTo("CME");
        assertThat(contract.lastTradeDateOrContractMonth()).isEqualTo("202506");
        assertThat(contract.localSymbol()).isEqualTo("ESM5");
    }

    @Test
    void build_futures_withoutContractMonth_doesNotSetLastTradeDate() {
        OrderDTO order = buildOrderDTO("ES", "CME", "FUT", null);

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("ES");
        assertThat(contract.secType().getApiString()).isEqualTo("FUT");
        assertThat(contract.lastTradeDateOrContractMonth()).isNullOrEmpty();
    }

    @Test
    void build_futures_nonEmini_doesNotSetLocalSymbol() {
        OrderDTO order = buildOrderDTO("CL", "NYMEX", "FUT", "202507");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.symbol()).isEqualTo("CL");
        assertThat(contract.lastTradeDateOrContractMonth()).isEqualTo("202507");
        assertThat(contract.localSymbol()).isNullOrEmpty();
    }

    @Test
    void buildFuturesContract_nqWithContractMonth_setsLocalSymbol() {
        Contract contract = contractBuilder.buildFuturesContract("NQ", "CME", "202506");

        assertThat(contract.symbol()).isEqualTo("NQ");
        assertThat(contract.secType().getApiString()).isEqualTo("FUT");
        assertThat(contract.exchange()).isEqualTo("CME");
        assertThat(contract.lastTradeDateOrContractMonth()).isEqualTo("202506");
        assertThat(contract.localSymbol()).isEqualTo("NQM5");
    }

    @Test
    void buildFuturesContract_nullExchange_usesDefaultExchange() {
        Contract contract = contractBuilder.buildFuturesContract("GC", null, "202508");

        assertThat(contract.exchange()).isEqualTo("COMEX");
    }

    @Test
    void build_futuresAlias_handledCorrectly() {
        OrderDTO order = buildOrderDTO("ES", "CME", "FUTURES", "202506");

        Contract contract = contractBuilder.build(order);

        assertThat(contract.secType().getApiString()).isEqualTo("FUT");
        assertThat(contract.localSymbol()).isEqualTo("ESM5");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private OrderDTO buildOrderDTO(String symbol, String exchange, String assetClass) {
        return buildOrderDTO(symbol, exchange, assetClass, null);
    }

    private OrderDTO buildOrderDTO(String symbol, String exchange, String assetClass,
                                    String contractMonth) {
        return new OrderDTO(
                UUID.randomUUID(), symbol, exchange, assetClass,
                Order.OrderSide.BUY, Order.OrderType.LIMIT,
                BigDecimal.valueOf(10), BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(140.00), BigDecimal.valueOf(165.00),
                "FVG", Instant.now().plusSeconds(300),
                BigDecimal.valueOf(300), 2.0, 4.0, 6.0,
                BigDecimal.valueOf(600), 2.0, "FIXED_FRACTIONAL", contractMonth
        );
    }
}
