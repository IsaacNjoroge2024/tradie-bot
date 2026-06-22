package com.tradie.strategy.forex;

import com.tradie.common.entity.CurrencyPair;
import com.tradie.common.repository.CurrencyPairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyPairServiceTest {

    @Mock
    private CurrencyPairRepository currencyPairRepository;

    private CurrencyPairService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyPairService(currencyPairRepository);
    }

    // ─── normalizePair ────────────────────────────────────────────────────────

    @Test
    void normalizePair_slashFormat_removesSlash() {
        assertThat(CurrencyPairService.normalizePair("EUR/USD")).isEqualTo("EURUSD");
    }

    @Test
    void normalizePair_dotFormat_removesDot() {
        assertThat(CurrencyPairService.normalizePair("EUR.USD")).isEqualTo("EURUSD");
    }

    @Test
    void normalizePair_alreadyNormalized_unchanged() {
        assertThat(CurrencyPairService.normalizePair("EURUSD")).isEqualTo("EURUSD");
    }

    @Test
    void normalizePair_lowercase_uppercased() {
        assertThat(CurrencyPairService.normalizePair("eur/usd")).isEqualTo("EURUSD");
    }

    @Test
    void normalizePair_withWhitespace_stripped() {
        assertThat(CurrencyPairService.normalizePair(" EUR / USD ")).isEqualTo("EURUSD");
    }

    @Test
    void normalizePair_null_throwsException() {
        assertThatThrownBy(() -> CurrencyPairService.normalizePair(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency pair symbol is required");
    }

    @Test
    void normalizePair_blank_throwsException() {
        assertThatThrownBy(() -> CurrencyPairService.normalizePair("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency pair symbol is required");
    }

    @Test
    void normalizePair_malformedSymbol_throwsException() {
        assertThatThrownBy(() -> CurrencyPairService.normalizePair("EURUSDX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency pair symbol format");
    }

    // ─── findPair ─────────────────────────────────────────────────────────────

    @Test
    void findPair_found_returnsOptional() {
        CurrencyPair pair = buildPair("EURUSD", "EUR", "USD");
        when(currencyPairRepository.findById("EURUSD")).thenReturn(Optional.of(pair));

        Optional<CurrencyPair> result = service.findPair("EUR/USD");

        assertThat(result).isPresent();
        assertThat(result.get().getSymbol()).isEqualTo("EURUSD");
    }

    @Test
    void findPair_notFound_returnsEmpty() {
        when(currencyPairRepository.findById("XYZABC")).thenReturn(Optional.empty());

        Optional<CurrencyPair> result = service.findPair("XYZABC");

        assertThat(result).isEmpty();
    }

    // ─── getPairOrDefault ─────────────────────────────────────────────────────

    @Test
    void getPairOrDefault_found_returnsPair() {
        CurrencyPair pair = buildPair("EURUSD", "EUR", "USD");
        when(currencyPairRepository.findById("EURUSD")).thenReturn(Optional.of(pair));

        CurrencyPair result = service.getPairOrDefault("EUR/USD");

        assertThat(result.getSymbol()).isEqualTo("EURUSD");
        assertThat(result.getBaseCurrency()).isEqualTo("EUR");
        assertThat(result.getQuoteCurrency()).isEqualTo("USD");
    }

    @Test
    void getPairOrDefault_notFound_returnsDefaultWithExtractedCurrencies() {
        when(currencyPairRepository.findById("XYZABC")).thenReturn(Optional.empty());

        CurrencyPair result = service.getPairOrDefault("XYZABC");

        assertThat(result.getSymbol()).isEqualTo("XYZABC");
        assertThat(result.getBaseCurrency()).isEqualTo("XYZ");
        assertThat(result.getQuoteCurrency()).isEqualTo("ABC");
        assertThat(result.getPipPosition()).isEqualTo(4);
        assertThat(result.getPipValuePerStandardLot()).isEqualTo(10.0);
        assertThat(result.getMinLotSize()).isEqualTo(0.01);
        assertThat(result.getMarginRate()).isEqualTo(0.02);
    }

    @Test
    void getPairOrDefault_notFoundJpyPair_defaultHasJpyPipPosition() {
        when(currencyPairRepository.findById("USDJPY")).thenReturn(Optional.empty());

        CurrencyPair result = service.getPairOrDefault("USDJPY");

        assertThat(result.getPipPosition()).isEqualTo(2);
        assertThat(result.getPipValuePerStandardLot()).isEqualTo(6.67);
    }

    @Test
    void getPairOrDefault_notFoundShortSymbol_setsUsdQuote() {
        when(currencyPairRepository.findById("EUR")).thenReturn(Optional.empty());

        CurrencyPair result = service.getPairOrDefault("EUR");

        assertThat(result.getBaseCurrency()).isEqualTo("EUR");
        assertThat(result.getQuoteCurrency()).isEqualTo("USD");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private CurrencyPair buildPair(String symbol, String base, String quote) {
        CurrencyPair pair = new CurrencyPair();
        pair.setSymbol(symbol);
        pair.setBaseCurrency(base);
        pair.setQuoteCurrency(quote);
        pair.setPipPosition(4);
        pair.setPipValuePerStandardLot(10.0);
        pair.setMinLotSize(0.01);
        pair.setLotStep(0.01);
        pair.setMarginRate(0.02);
        pair.setCategory("MAJOR");
        return pair;
    }
}
