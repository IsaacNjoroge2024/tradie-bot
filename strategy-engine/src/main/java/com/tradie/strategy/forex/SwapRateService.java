package com.tradie.strategy.forex;

import com.tradie.strategy.forex.dto.SwapRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Calculates swap (rollover) costs for forex positions held overnight.
 *
 * <p>Swap rates are approximate broker-typical values in points per standard lot.
 * Triple swap applies on Wednesday due to T+2 settlement (weekends are settled on Wednesday).
 */
@Service
public class SwapRateService {

    private static final Logger log = LoggerFactory.getLogger(SwapRateService.class);

    private static final Map<String, SwapRate> SWAP_RATES = Map.ofEntries(
            Map.entry("EURUSD", new SwapRate("EURUSD",  -5.0,  3.0, DayOfWeek.WEDNESDAY)),
            Map.entry("GBPUSD", new SwapRate("GBPUSD",  -4.0,  2.5, DayOfWeek.WEDNESDAY)),
            Map.entry("USDJPY", new SwapRate("USDJPY",   4.0, -6.0, DayOfWeek.WEDNESDAY)),
            Map.entry("USDCHF", new SwapRate("USDCHF",   2.0, -4.0, DayOfWeek.WEDNESDAY)),
            Map.entry("AUDUSD", new SwapRate("AUDUSD",   3.0, -5.0, DayOfWeek.WEDNESDAY)),
            Map.entry("USDCAD", new SwapRate("USDCAD",  -3.0,  1.5, DayOfWeek.WEDNESDAY)),
            Map.entry("NZDUSD", new SwapRate("NZDUSD",   2.0, -4.0, DayOfWeek.WEDNESDAY)),
            Map.entry("EURGBP", new SwapRate("EURGBP",  -3.0,  1.0, DayOfWeek.WEDNESDAY)),
            Map.entry("EURJPY", new SwapRate("EURJPY",   2.0, -4.0, DayOfWeek.WEDNESDAY)),
            Map.entry("GBPJPY", new SwapRate("GBPJPY",   3.0, -5.0, DayOfWeek.WEDNESDAY))
    );

    private static final SwapRate DEFAULT_SWAP =
            new SwapRate("UNKNOWN", -2.0, -2.0, DayOfWeek.WEDNESDAY);

    /**
     * Returns the swap rate for a currency pair.
     *
     * @param pair currency pair symbol (any format)
     * @return swap rate, or a conservative default for unknown pairs
     */
    public SwapRate getSwapRate(String pair) {
        return SWAP_RATES.getOrDefault(CurrencyPairService.normalizePair(pair), DEFAULT_SWAP);
    }

    /**
     * Calculates the total swap cost for holding a position overnight.
     *
     * @param pair   currency pair symbol
     * @param side   "BUY" for long, "SELL" for short
     * @param lots   position size in standard lots
     * @param nights number of nights the position is held
     * @return total swap cost in points (negative = debit, positive = credit)
     */
    public double calculateSwapCost(String pair, String side, double lots, int nights) {
        SwapRate rate = getSwapRate(pair);
        double swapPoints = "BUY".equalsIgnoreCase(side)
                ? rate.longSwapPoints()
                : rate.shortSwapPoints();

        int effectiveNights = calculateEffectiveNights(nights);
        double cost = swapPoints * lots * effectiveNights;

        log.debug("Swap cost for {} {} {} lots over {} nights (effective {}): {} points",
                side, lots, pair, nights, effectiveNights, cost);
        return cost;
    }

    /**
     * Adjusts nights for triple swap: every 7-night period that includes a Wednesday
     * adds 2 extra nights (T+2 settlement covers the weekend).
     */
    int calculateEffectiveNights(int nights) {
        return nights + (nights / 7) * 2;
    }
}
