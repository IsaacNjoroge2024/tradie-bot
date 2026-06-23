package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Computes back-adjusted price factors for continuous futures contracts.
 *
 * <p>Uses the ratio method: at each rollover point the price ratio between the
 * expiring and the next contract is multiplied into a running adjustment factor
 * that is applied to all older contracts. This ensures historical price series
 * are continuous at the rollover point, which is required for indicators
 * (RSI, MACD, Bollinger Bands, etc.) to calculate correctly across contract boundaries.
 *
 * <p>The computed factors are informational — they are returned to the caller for
 * use during backtesting (Ticket 18) rather than being persisted to the database.
 */
@Service
public class ContinuousContractService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousContractService.class);

    private final FuturesContractRepository repository;

    public ContinuousContractService(FuturesContractRepository repository) {
        this.repository = repository;
    }

    /**
     * Computes the back-adjustment factor for each historical contract of the given root symbol.
     *
     * <p>Contracts are ordered by expiration date ascending. Starting from the front-month
     * contract (factor = 1.0), each prior contract's factor is the cumulative product of all
     * rollover ratios from that contract through to the front month.
     *
     * <p>Each entry in {@code rolloverRatios} is the price ratio at one rollover event:
     * {@code rolloverRatios[i] = price_of_contract[i+1] / price_of_contract[i]} at the moment
     * of rollover. The caller computes this from the two live prices on roll day.
     *
     * @param symbol         root futures symbol (e.g., "ES")
     * @param rolloverRatios price ratios at each rollover, ordered oldest-to-newest;
     *                       must have length equal to the number of active contracts minus one;
     *                       each value must be positive
     * @return adjustment factors per contract, index 0 = oldest, last index = front month (1.0)
     * @throws IllegalArgumentException if rolloverRatios length does not match contracts count - 1,
     *                                  or if any ratio is not positive
     */
    public double[] computeAdjustmentFactors(String symbol, double[] rolloverRatios) {
        List<FuturesContract> contracts = repository.findBySymbolAndActiveTrue(symbol).stream()
                .filter(c -> c.getExpirationDate() != null)
                .sorted(Comparator.comparing(FuturesContract::getExpirationDate))
                .toList();

        if (contracts.size() < 2) {
            log.debug("Fewer than 2 active contracts for {}: no adjustment needed", symbol);
            return new double[]{1.0};
        }

        int n = contracts.size();
        if (rolloverRatios == null || rolloverRatios.length != n - 1) {
            throw new IllegalArgumentException(
                    String.format("Expected %d rollover ratios for %d contracts of %s, got %d",
                            n - 1, n, symbol, rolloverRatios == null ? 0 : rolloverRatios.length));
        }

        double[] factors = new double[n];
        factors[n - 1] = 1.0;

        // Walk backwards: each older contract accumulates the product of all subsequent ratios.
        // rolloverRatios[i] = new_contract_price / old_contract_price at rollover i.
        double cumulativeRatio = 1.0;
        for (int i = n - 2; i >= 0; i--) {
            if (rolloverRatios[i] <= 0) {
                throw new IllegalArgumentException(
                        "Rollover ratio at index " + i + " must be positive, got: " + rolloverRatios[i]);
            }
            cumulativeRatio *= rolloverRatios[i];
            factors[i] = cumulativeRatio;
            log.debug("Contract {} adjustment factor: {}", contracts.get(i).getFullSymbol(), cumulativeRatio);
        }

        return factors;
    }
}
