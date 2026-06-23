package com.tradie.strategy.futures;

import com.tradie.strategy.config.FuturesProperties;
import com.tradie.strategy.futures.dto.FuturesPositionSize;
import com.tradie.strategy.futures.dto.FuturesSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calculates futures position sizes in whole contracts based on tick-value risk.
 *
 * <p>Formula: contracts = floor(riskAmount / (ticksToStop × tickValue)), minimum 1.
 * Margin validation rejects the trade if the required margin exceeds the configured buffer.
 */
@Component
public class FuturesPositionSizer {

    private static final Logger log = LoggerFactory.getLogger(FuturesPositionSizer.class);

    private final FuturesContractService contractService;
    private final FuturesProperties futuresProperties;

    public FuturesPositionSizer(FuturesContractService contractService,
                                 FuturesProperties futuresProperties) {
        this.contractService = contractService;
        this.futuresProperties = futuresProperties;
    }

    /**
     * Calculates the optimal number of contracts for a futures trade.
     *
     * @param symbol         root futures symbol (e.g., "ES")
     * @param accountBalance account balance in USD
     * @param riskPercentage percentage of account to risk (e.g., 2.0 for 2%)
     * @param entryPrice     entry price
     * @param stopLossPrice  stop-loss price
     * @return calculated position size details
     * @throws InsufficientMarginException if margin required exceeds the configured buffer
     */
    public FuturesPositionSize calculate(String symbol, double accountBalance,
                                          double riskPercentage, double entryPrice,
                                          double stopLossPrice) {
        FuturesSpec spec = contractService.getFrontMonthContract(symbol)
                .orElseGet(() -> {
                    log.warn("No front-month contract found for {}, using built-in spec", symbol);
                    return getBuiltInSpec(symbol);
                });

        double riskAmount = accountBalance * (riskPercentage / 100.0);
        double priceDistance = Math.abs(entryPrice - stopLossPrice);
        double ticksToStop = spec.tickSize() > 0 ? priceDistance / spec.tickSize() : 0;
        double riskPerContract = ticksToStop * spec.tickValue();

        int contracts = riskPerContract > 0
                ? (int) Math.floor(riskAmount / riskPerContract)
                : 0;
        contracts = Math.max(1, contracts);

        double marginRequired = contracts * spec.initialMargin();
        validateMargin(marginRequired, accountBalance);

        double notionalValue = contracts * entryPrice * spec.multiplier();

        log.debug("Futures sizing for {}: {}c × {} ticks × ${}/tick = ${} risk, margin={}",
                symbol, contracts, ticksToStop, spec.tickValue(), riskAmount, marginRequired);

        return new FuturesPositionSize(contracts, notionalValue, riskAmount, ticksToStop, marginRequired);
    }

    private void validateMargin(double marginRequired, double accountBalance) {
        double limit = accountBalance * (1.0 - futuresProperties.getMarginBufferPct() / 100.0);
        if (marginRequired > limit) {
            throw new InsufficientMarginException(
                    String.format("Required margin %.2f exceeds %.0f%% buffer of available funds %.2f",
                            marginRequired, futuresProperties.getMarginBufferPct(), accountBalance));
        }
    }

    /**
     * Hard-coded fallback specs matching the ticket's common futures table.
     * Used only when the DB has no front-month contract for the symbol.
     */
    FuturesSpec getBuiltInSpec(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "ES"  -> new FuturesSpec("ES",  "ES",  null, "CME",    50.0, 0.25, 12.50, null, 12000.0, 10800.0);
            case "NQ"  -> new FuturesSpec("NQ",  "NQ",  null, "CME",    20.0, 0.25,  5.00, null, 18000.0, 16200.0);
            case "MES" -> new FuturesSpec("MES", "MES", null, "CME",     5.0, 0.25,  1.25, null,  1200.0,  1080.0);
            case "MNQ" -> new FuturesSpec("MNQ", "MNQ", null, "CME",     2.0, 0.25,  0.50, null,  1800.0,  1620.0);
            case "CL"  -> new FuturesSpec("CL",  "CL",  null, "NYMEX", 1000.0, 0.01, 10.00, null,  8000.0,  7200.0);
            case "GC"  -> new FuturesSpec("GC",  "GC",  null, "COMEX",  100.0, 0.10, 10.00, null, 10000.0,  9000.0);
            default    -> new FuturesSpec(symbol, symbol, null, "CME",   50.0, 0.25, 12.50, null, 12000.0, 10800.0);
        };
    }
}
