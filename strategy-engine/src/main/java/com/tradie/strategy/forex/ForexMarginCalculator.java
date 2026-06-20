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
     * @param pair         currency pair symbol
     * @param lots         position size in standard lots
     * @param currentPrice current market price
     * @return margin required in USD
     */
    public double calculateMargin(String pair, double lots, double currentPrice) {
        CurrencyPair currencyPair = currencyPairService.getPairOrDefault(pair);
        double notionalValue = lots * STANDARD_LOT_UNITS * currentPrice;
        return notionalValue * currencyPair.getMarginRate();
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
