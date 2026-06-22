package com.tradie.strategy.forex;

import com.tradie.common.entity.CurrencyPair;
import com.tradie.common.repository.CurrencyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrencyPairService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyPairService.class);

    private final CurrencyPairRepository currencyPairRepository;

    public CurrencyPairService(CurrencyPairRepository currencyPairRepository) {
        this.currencyPairRepository = currencyPairRepository;
    }

    /**
     * Looks up a currency pair by symbol.
     *
     * @param symbol raw symbol (e.g., "EUR/USD", "EURUSD")
     * @return Optional containing the pair if found
     */
    public Optional<CurrencyPair> findPair(String symbol) {
        return currencyPairRepository.findById(normalizePair(symbol));
    }

    /**
     * Looks up a currency pair, falling back to sensible defaults for unknown pairs.
     *
     * @param symbol raw symbol (e.g., "EUR/USD", "EURUSD")
     * @return the pair definition, or a default if unknown
     */
    public CurrencyPair getPairOrDefault(String symbol) {
        String normalized = normalizePair(symbol);
        return currencyPairRepository.findById(normalized)
                .orElseGet(() -> {
                    log.warn("Unknown currency pair: {} - applying default pip/margin values", symbol);
                    return buildDefault(normalized);
                });
    }

    /**
     * Normalizes a pair symbol to the 6-character uppercase format without separators.
     * Examples: "EUR/USD" → "EURUSD", "eur.usd" → "EURUSD", " eur / usd " → "EURUSD"
     */
    public static String normalizePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Currency pair symbol is required");
        }
        return symbol.trim()
                .replaceAll("\\s+", "")
                .replace("/", "")
                .replace(".", "")
                .toUpperCase();
    }

    private CurrencyPair buildDefault(String normalized) {
        CurrencyPair pair = new CurrencyPair();
        pair.setSymbol(normalized);
        if (normalized.length() >= 6) {
            pair.setBaseCurrency(normalized.substring(0, 3));
            pair.setQuoteCurrency(normalized.substring(3, 6));
        } else {
            pair.setBaseCurrency(normalized);
            pair.setQuoteCurrency("USD");
        }
        boolean isJpy = normalized.endsWith("JPY");
        pair.setPipPosition(isJpy ? 2 : 4);
        pair.setPipValuePerStandardLot(isJpy ? 6.67 : 10.0);
        pair.setMinLotSize(0.01);
        pair.setLotStep(0.01);
        pair.setMarginRate(0.02);
        pair.setCategory("MAJOR");
        return pair;
    }
}
