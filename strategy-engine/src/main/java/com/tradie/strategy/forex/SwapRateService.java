package com.tradie.strategy.forex;

import com.tradie.strategy.forex.dto.SwapRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Calculates swap (rollover) costs for forex positions held overnight.
 *
 * <p>Swap rates are approximate broker-typical values in points per standard lot.
 * Triple swap applies on the pair's {@code tripleSwapDay} (usually Wednesday) due to
 * T+2 settlement — weekends are settled on Wednesday, tripling the overnight cost.
 * The crossing count is computed from the actual open date rather than a fixed 7-day divisor,
 * so short holds that cross the triple-swap day are accounted for correctly.
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
     * <p>Triple swap is applied accurately by counting how many times the pair's
     * {@code tripleSwapDay} falls within the holding window starting at {@code openedAt}.
     * Each such day adds 2 extra nightly charges (T+2 settlement for the weekend).
     *
     * @param pair     currency pair symbol
     * @param side     "BUY" for long, "SELL" for short
     * @param lots     position size in standard lots
     * @param nights   number of nights the position is held
     * @param openedAt UTC instant when the position was opened
     * @return total swap cost in points (negative = debit, positive = credit)
     */
    public double calculateSwapCost(String pair, String side, double lots, int nights, Instant openedAt) {
        if (lots < 0 || nights < 0) {
            throw new IllegalArgumentException("lots and nights must be non-negative");
        }
        SwapRate rate = getSwapRate(pair);
        double swapPoints;
        if ("BUY".equalsIgnoreCase(side)) {
            swapPoints = rate.longSwapPoints();
        } else if ("SELL".equalsIgnoreCase(side)) {
            swapPoints = rate.shortSwapPoints();
        } else {
            throw new IllegalArgumentException("side must be BUY or SELL, got: " + side);
        }

        int tripleSwapCount = countTripleSwapNights(openedAt, nights, rate.tripleSwapDay());
        int effectiveNights = nights + tripleSwapCount * 2;
        double cost = swapPoints * lots * effectiveNights;

        log.debug("Swap cost for {} {} {} lots over {} nights (effective {}, {} triple-swap crossings): {} points",
                side, lots, pair, nights, effectiveNights, tripleSwapCount, cost);
        return cost;
    }

    /**
     * Counts how many times {@code tripleSwapDay} occurs within the holding window.
     * The window covers nights {@code 0..nights-1}, each mapped to the UTC calendar day
     * {@code openedAt + i days}.
     */
    int countTripleSwapNights(Instant openedAt, int nights, DayOfWeek tripleSwapDay) {
        LocalDate startDate = openedAt.atZone(ZoneOffset.UTC).toLocalDate();
        int count = 0;
        for (int i = 0; i < nights; i++) {
            if (startDate.plusDays(i).getDayOfWeek() == tripleSwapDay) {
                count++;
            }
        }
        return count;
    }
}
