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
 *   <li>FUT    – futures routed via the exchange specified in the order</li>
 *   <li>CRYPTO – crypto via Paxos on IBKR</li>
 * </ul>
 */
@Component
public class ContractBuilder {

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
            case "FUT" -> {
                contract.secType("FUT");
                contract.exchange(order.exchange());
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
     * Parses a forex pair symbol into [baseCurrency, quoteCurrency].
     * Handles formats: "EURUSD", "EUR/USD", "EUR.USD", "EUR" (fallback USD quote).
     */
    private String[] parseForexPair(String symbol) {
        if (symbol.contains("/")) {
            String[] parts = symbol.split("/");
            return new String[]{parts[0].trim().toUpperCase(), parts[1].trim().toUpperCase()};
        }
        String normalized = symbol.replace(".", "").toUpperCase();
        if (normalized.length() >= 6) {
            return new String[]{normalized.substring(0, 3), normalized.substring(3, 6)};
        }
        return new String[]{normalized, "USD"};
    }
}
