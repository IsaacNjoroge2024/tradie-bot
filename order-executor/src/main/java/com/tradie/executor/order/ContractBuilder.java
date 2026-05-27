package com.tradie.executor.order;

import com.ib.client.Contract;
import com.tradie.executor.dto.OrderDTO;
import org.springframework.stereotype.Component;

/**
 * Builds IBKR {@link Contract} objects for different asset classes.
 *
 * <ul>
 *   <li>STK  – stocks routed via SMART exchange</li>
 *   <li>CASH / FOREX – forex pairs routed via IDEALPRO</li>
 *   <li>FUT  – futures routed via the exchange specified in the order</li>
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
            default -> {
                // Default to stock if asset class is unrecognised
                contract.secType("STK");
                contract.exchange("SMART");
            }
        }

        return contract;
    }
}
