package com.tradie.strategy.forex;

import com.tradie.common.entity.CurrencyPair;
import org.springframework.stereotype.Component;

/**
 * Calculates margin requirements for forex positions.
 * Margin = notional value × margin rate, with a 10% safety buffer check.
 */
@Component
public class ForexMarginCalculator {

    private static final int STANDARD_LOT_UNITS = 100_000;
    private static final double MARGIN_BUFFER = 0.9;

    private final CurrencyPairService currencyPairService;

    public ForexMarginCalculator(CurrencyPairService currencyPairService) {
        this.currencyPairService = currencyPairService;
    }

    /**
     * Calculates the USD margin required to open a forex position.
     *
     * <ul>
     *   <li>USD-base pairs (e.g. USD/JPY): notional = lots × 100,000 (already in USD)</li>
     *   <li>USD-quote pairs (e.g. EUR/USD): notional = lots × 100,000 × currentPrice</li>
     *   <li>Cross pairs (e.g. EUR/GBP): throws — a separate quote→USD rate is required</li>
     * </ul>
     *
     * @param pair         currency pair symbol
     * @param lots         position size in standard lots
     * @param currentPrice current market price
     * @return margin required in USD
     */
    public double calculateMargin(String pair, double lots, double currentPrice) {
        CurrencyPair currencyPair = currencyPairService.getPairOrDefault(pair);
        double units = lots * STANDARD_LOT_UNITS;
        double notionalUsd;

        if ("USD".equals(currencyPair.getBaseCurrency())) {
            notionalUsd = units;
        } else if ("USD".equals(currencyPair.getQuoteCurrency())) {
            notionalUsd = units * currentPrice;
        } else {
            throw new IllegalArgumentException(
                    "Cross pair requires quote->USD conversion before margin calculation: " + pair);
        }

        return notionalUsd * currencyPair.getMarginRate();
    }

    /**
     * Checks whether sufficient margin is available, applying a 10% safety buffer.
     *
     * @param marginRequired  margin needed for the trade
     * @param availableMargin account's available margin
     * @return true if margin required does not exceed 90% of available margin
     */
    public boolean hasEnoughMargin(double marginRequired, double availableMargin) {
        return marginRequired <= availableMargin * MARGIN_BUFFER;
    }
}
