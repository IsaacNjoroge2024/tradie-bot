package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import com.tradie.strategy.futures.dto.FuturesSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FuturesContractService {

    private static final Logger log = LoggerFactory.getLogger(FuturesContractService.class);

    private final FuturesContractRepository repository;

    public FuturesContractService(FuturesContractRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the spec for a specific full symbol (e.g., "ESH5").
     */
    public Optional<FuturesSpec> getContractSpec(String fullSymbol) {
        return repository.findById(fullSymbol).map(this::toSpec);
    }

    /**
     * Returns the active front-month contract spec for a root symbol (e.g., "ES").
     * Returns empty if no active front-month row exists; callers fall back to built-in specs.
     */
    public Optional<FuturesSpec> getFrontMonthContract(String symbol) {
        Optional<FuturesSpec> db = repository.findBySymbolAndActiveTrueAndFrontMonthTrue(symbol)
                .map(this::toSpec);
        if (db.isEmpty()) {
            log.warn("No active front-month contract found in DB for symbol: {}", symbol);
        }
        return db;
    }

    /**
     * Returns all active contracts for a root symbol.
     */
    public List<FuturesSpec> getActiveContracts(String symbol) {
        return repository.findBySymbolAndActiveTrue(symbol).stream()
                .map(this::toSpec)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the given full symbol expires within {@code daysThreshold} calendar days.
     */
    public boolean isNearExpiration(String fullSymbol, int daysThreshold) {
        return repository.findById(fullSymbol)
                .map(c -> {
                    LocalDate exp = c.getExpirationDate();
                    if (exp == null) return false;
                    LocalDate today = LocalDate.now();
                    return !today.isAfter(exp) && !today.plusDays(daysThreshold).isBefore(exp);
                })
                .orElse(false);
    }

    private FuturesSpec toSpec(FuturesContract c) {
        double initMargin = c.getInitialMargin() != null ? c.getInitialMargin() : 0.0;
        double maintMargin = c.getMaintenanceMargin() != null ? c.getMaintenanceMargin() : 0.0;
        if (initMargin == 0.0 || maintMargin == 0.0) {
            log.warn("Futures contract {} has missing margin data — spec will fail validation", c.getFullSymbol());
        }
        return new FuturesSpec(
                c.getSymbol(),
                c.getFullSymbol(),
                c.getContractMonth(),
                c.getExchange(),
                c.getMultiplier(),
                c.getTickSize(),
                c.getTickValue(),
                c.getExpirationDate(),
                initMargin,
                maintMargin
        );
    }
}
