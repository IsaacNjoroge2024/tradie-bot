package com.tradie.executor.order;

import com.ib.client.Contract;
import com.tradie.executor.dto.OrderDTO;
import org.springframework.stereotype.Component;

/**
 * Builds IBKR {@link Contract} objects for different asset classes.
 *
 * <ul>
 *   <li>STK    – stocks routed via SMART exchange</li>
 *   <li>CASH / FOREX – forex pairs routed via IDEALPRO; symbol parsed into base/quote currency</li>
 *   <li>FUT    – futures with contract month and local symbol resolved automatically</li>
 *   <li>CRYPTO – crypto via Paxos on IBKR</li>
 * </ul>
 */
@Component
public class ContractBuilder {

    private static final java.util.Map<Integer, Character> MONTH_CODES = java.util.Map.ofEntries(
            java.util.Map.entry(1,  'F'), java.util.Map.entry(2,  'G'), java.util.Map.entry(3,  'H'),
            java.util.Map.entry(4,  'J'), java.util.Map.entry(5,  'K'), java.util.Map.entry(6,  'M'),
            java.util.Map.entry(7,  'N'), java.util.Map.entry(8,  'Q'), java.util.Map.entry(9,  'U'),
            java.util.Map.entry(10, 'V'), java.util.Map.entry(11, 'X'), java.util.Map.entry(12, 'Z')
    );

    private static final java.util.Set<String> EMINI_SYMBOLS =
            java.util.Set.of("ES", "NQ", "MES", "MNQ");

    /**
     * Builds an IBKR Contract from the validated {@link OrderDTO}.
     *
     * @param order the order containing symbol, assetClass, and exchange info
     * @return a configured IBKR Contract ready for order submission
     */
    public Contract build(OrderDTO order) {
        Contract contract = new Contract();
        contract.symbol(order.symbol());
        contract.currency("USD");

        switch (order.assetClass().toUpperCase()) {
            case "STK" -> {
                contract.secType("STK");
                contract.exchange("SMART");
            }
            case "CASH", "FOREX" -> {
                // IBKR requires symbol=base currency and currency=quote currency
                // e.g., "EURUSD" → symbol="EUR", currency="USD"
                String[] parts = parseForexPair(order.symbol());
                contract.symbol(parts[0]);
                contract.currency(parts[1]);
                contract.secType("CASH");
                contract.exchange("IDEALPRO");
            }
            case "FUT", "FUTURES" -> {
                String contractMonth = order.contractMonth();
                return buildFuturesContract(order.symbol(), order.exchange(), contractMonth);
            }
            case "CRYPTO" -> {
                contract.secType("CRYPTO");
                contract.exchange("PAXOS");
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported assetClass: " + order.assetClass());
        }

        return contract;
    }

    /**
     * Builds an IBKR futures Contract with contract month and local symbol populated.
     *
     * @param symbol        root futures symbol (e.g., "ES")
     * @param exchange      exchange override; if null, the built-in default is used
     * @param contractMonth contract month in YYYYMM format (e.g., "202506"); may be null
     * @return configured IBKR futures Contract
     */
    public Contract buildFuturesContract(String symbol, String exchange, String contractMonth) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("FUT");
        contract.currency("USD");
        contract.exchange(exchange != null && !exchange.isBlank() ? exchange : resolveExchange(symbol));

        if (contractMonth != null && !contractMonth.isBlank()) {
            contract.lastTradeDateOrContractMonth(contractMonth);
            // For E-mini contracts, also set the localSymbol (e.g., "ESH5")
            if (EMINI_SYMBOLS.contains(symbol.toUpperCase())) {
                String suffix = toLocalSymbolSuffix(contractMonth);
                if (suffix != null) {
                    contract.localSymbol(symbol.toUpperCase() + suffix);
                }
            }
        }

        return contract;
    }

    /**
     * Returns the default exchange for known futures root symbols.
     */
    private String resolveExchange(String symbol) {
        if (symbol == null) return "CME";
        return switch (symbol.toUpperCase()) {
            case "CL", "NG", "RB", "HO" -> "NYMEX";
            case "GC", "SI", "HG", "PL" -> "COMEX";
            default -> "CME";
        };
    }

    /**
     * Converts a YYYYMM contract month string to the two-character local-symbol suffix
     * (month code + last year digit). E.g., "202506" → "M5".
     * Returns null if the input is malformed.
     */
    private String toLocalSymbolSuffix(String contractMonth) {
        if (contractMonth == null || contractMonth.length() != 6) return null;
        try {
            int year  = Integer.parseInt(contractMonth.substring(0, 4));
            int month = Integer.parseInt(contractMonth.substring(4, 6));
            if (month < 1 || month > 12) return null;
            char code = MONTH_CODES.get(month);
            return "" + code + (year % 10);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a forex pair symbol into [baseCurrency, quoteCurrency].
     * Handles formats: "EURUSD", "EUR/USD", "EUR.USD", "EUR" (fallback USD quote).
     * Throws {@link IllegalArgumentException} for null, blank, or malformed symbols.
     */
    private String[] parseForexPair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Forex symbol is required");
        }
        if (symbol.contains("/")) {
            String[] parts = symbol.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid forex symbol format: " + symbol);
            }
            String base = parts[0].trim().toUpperCase();
            String quote = parts[1].trim().toUpperCase();
            if (base.length() != 3 || quote.length() != 3) {
                throw new IllegalArgumentException("Invalid forex currencies: " + symbol);
            }
            return new String[]{base, quote};
        }
        String normalized = symbol.replace(".", "").toUpperCase();
        if (normalized.length() == 6) {
            return new String[]{normalized.substring(0, 3), normalized.substring(3, 6)};
        }
        if (normalized.length() == 3) {
            return new String[]{normalized, "USD"};
        }
        throw new IllegalArgumentException("Invalid forex symbol format: " + symbol);
    }
}
