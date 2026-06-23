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
     * Computes the back-adjustment ratio for each historical contract of the given root symbol.
     *
     * <p>Contracts are ordered by expiration date ascending. Starting from the front-month
     * contract (ratio = 1.0), each prior contract's ratio is multiplied by the price ratio
     * at the rollover point between adjacent contracts.
     *
     * @param symbol      root futures symbol (e.g., "ES")
     * @param rollPrices  closing prices at each rollover point, ordered oldest-to-newest;
     *                    must have length equal to the number of active contracts minus one
     * @return adjustment factors per contract, index 0 = oldest, last = front month (1.0)
     * @throws IllegalArgumentException if rollPrices length does not match contracts count - 1
     */
    public double[] computeAdjustmentFactors(String symbol, double[] rollPrices) {
        List<FuturesContract> contracts = repository.findBySymbolAndActiveTrue(symbol).stream()
                .filter(c -> c.getExpirationDate() != null)
                .sorted(Comparator.comparing(FuturesContract::getExpirationDate))
                .toList();

        if (contracts.size() < 2) {
            log.debug("Fewer than 2 active contracts for {}: no adjustment needed", symbol);
            return new double[]{1.0};
        }

        int n = contracts.size();
        if (rollPrices == null || rollPrices.length != n - 1) {
            throw new IllegalArgumentException(
                    String.format("Expected %d roll prices for %d contracts of %s, got %d",
                            n - 1, n, symbol, rollPrices == null ? 0 : rollPrices.length));
        }

        double[] factors = new double[n];
        factors[n - 1] = 1.0;

        // Walk backwards from front month: each prior contract accumulates the ratio at rollover
        double cumulativeRatio = 1.0;
        for (int i = n - 2; i >= 0; i--) {
            double priceAtRollNext = rollPrices[i + 1 < rollPrices.length ? i + 1 : i];
            double priceAtRollCurrent = rollPrices[i];
            if (priceAtRollCurrent <= 0) {
                throw new IllegalArgumentException(
                        "Roll price at index " + i + " must be positive, got: " + priceAtRollCurrent);
            }
            double ratio = priceAtRollNext / priceAtRollCurrent;
            cumulativeRatio *= ratio;
            factors[i] = cumulativeRatio;
            log.debug("Contract {} adjustment factor: {}", contracts.get(i).getFullSymbol(), cumulativeRatio);
        }

        return factors;
    }
}
