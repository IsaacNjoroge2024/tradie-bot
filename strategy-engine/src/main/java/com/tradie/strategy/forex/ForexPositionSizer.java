package com.tradie.strategy.forex;

import com.tradie.strategy.config.ForexProperties;
import com.tradie.strategy.forex.dto.ForexPositionSize;
import org.springframework.stereotype.Component;

/**
 * Calculates forex position sizes in standard lots based on risk parameters.
 *
 * <p>Formula: lots = riskAmount / (stopLossPips × pipValuePerLot)
 * Result is clamped to [minLotSize, maxLotSize] and rounded down to the lot step.
 */
@Component
public class ForexPositionSizer {

    private static final int STANDARD_LOT_UNITS = 100_000;

    private final ForexPipCalculator pipCalculator;
    private final ForexMarginCalculator marginCalculator;
    private final ForexProperties forexProperties;

    public ForexPositionSizer(ForexPipCalculator pipCalculator,
                               ForexMarginCalculator marginCalculator,
                               ForexProperties forexProperties) {
        this.pipCalculator = pipCalculator;
        this.marginCalculator = marginCalculator;
        this.forexProperties = forexProperties;
    }

    /**
     * Calculates the optimal position size for a forex trade.
     *
     * @param pair            currency pair symbol (e.g., "EURUSD")
     * @param accountBalance  account balance in USD
     * @param riskPercentage  percentage of account to risk (e.g., 2.0 for 2%)
     * @param entryPrice      entry price
     * @param stopLossPips    stop loss distance in pips
     * @return calculated position size details
     */
    public ForexPositionSize calculate(String pair, double accountBalance,
                                        double riskPercentage, double entryPrice,
                                        double stopLossPips) {
        double riskAmount = accountBalance * (riskPercentage / 100.0);
        double pipValuePerLot = pipCalculator.getPipValue(pair, 1.0, entryPrice);

        double lots;
        if (stopLossPips <= 0 || pipValuePerLot <= 0) {
            lots = forexProperties.getMinLotSize();
        } else {
            lots = riskAmount / (stopLossPips * pipValuePerLot);
        }

        lots = clampLots(lots);
        lots = roundToLotStep(pair, lots);

        double units = lots * STANDARD_LOT_UNITS;
        double positionPipValue = pipCalculator.getPipValue(pair, lots, entryPrice);
        double marginRequired = marginCalculator.calculateMargin(pair, lots, entryPrice);

        return new ForexPositionSize(lots, units, getLotType(lots), riskAmount,
                positionPipValue, marginRequired);
    }

    /**
     * Rounds lot size down to the pair's valid lot step increment (default 0.01 micro lots).
     */
    public double roundToLotStep(String pair, double lots) {
        double step = forexProperties.getMinLotSize();
        return Math.floor(lots / step) * step;
    }

    /**
     * Classifies the lot size as STANDARD (≥1.0), MINI (≥0.1), or MICRO (<0.1).
     */
    public String getLotType(double lots) {
        if (lots >= 1.0) return "STANDARD";
        if (lots >= 0.1) return "MINI";
        return "MICRO";
    }

    private double clampLots(double lots) {
        return Math.min(Math.max(lots, forexProperties.getMinLotSize()),
                forexProperties.getMaxLotSize());
    }
}
