package com.tradie.strategy.futures;

import com.tradie.common.entity.FuturesContract;
import com.tradie.common.repository.FuturesContractRepository;
import com.tradie.strategy.config.FuturesProperties;
import com.tradie.strategy.futures.dto.RolloverAlert;
import com.tradie.strategy.service.OrderPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects upcoming futures contract rollovers.
 *
 * <p>Default rollover threshold is 8 business days before expiration (configurable via
 * {@code tradie.futures.default-rollover-days}).
 */
@Service
public class RolloverService {

    private static final Logger log = LoggerFactory.getLogger(RolloverService.class);

    private final FuturesContractRepository repository;
    private final FuturesProperties futuresProperties;
    private final OrderPublisher orderPublisher;

    public RolloverService(FuturesContractRepository repository,
                            FuturesProperties futuresProperties,
                            OrderPublisher orderPublisher) {
        this.repository = repository;
        this.futuresProperties = futuresProperties;
        this.orderPublisher = orderPublisher;
    }

    /**
     * Checks all active contracts and returns alerts for those due to roll within {@code daysAhead}.
     *
     * @param daysAhead number of calendar days to look ahead
     * @return list of rollover alerts, sorted by days remaining ascending
     */
    public List<RolloverAlert> checkForUpcomingRollovers(int daysAhead) {
        List<FuturesContract> actives = repository.findByActiveTrue();
        List<RolloverAlert> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (FuturesContract contract : actives) {
            if (contract.getExpirationDate() == null) continue;
            LocalDate rollDate = contract.getExpirationDate()
                    .minusDays(futuresProperties.getDefaultRolloverDays());
            long daysToRoll = ChronoUnit.DAYS.between(today, rollDate);

            if (daysToRoll >= 0 && daysToRoll <= daysAhead) {
                String nextSymbol = findNextContractSymbol(contract);
                alerts.add(new RolloverAlert(
                        contract.getSymbol(),
                        contract.getFullSymbol(),
                        nextSymbol,
                        rollDate,
                        (int) daysToRoll
                ));
                log.info("Upcoming rollover: {} → {} in {} day(s) (roll date: {})",
                        contract.getFullSymbol(), nextSymbol, daysToRoll, rollDate);
            }
        }

        alerts.sort(Comparator.comparingInt(RolloverAlert::daysRemaining));
        return alerts;
    }

    /**
     * Returns true if the given full symbol has reached or passed its rollover date.
     *
     * @param fullSymbol full contract symbol (e.g., "ESH5")
     */
    public boolean shouldRoll(String fullSymbol) {
        return repository.findById(fullSymbol)
                .map(contract -> {
                    if (contract.getExpirationDate() == null) return false;
                    LocalDate rollDate = contract.getExpirationDate()
                            .minusDays(futuresProperties.getDefaultRolloverDays());
                    return !LocalDate.now().isBefore(rollDate);
                })
                .orElse(false);
    }

    /**
     * Runs daily at 08:00 UTC and publishes a rollover alert to the {@code tradie.alerts}
     * Kafka topic for every contract within the configured rollover window.
     *
     * <p>The alert-service consumes this topic and formats the message for Telegram delivery:
     * <pre>
     * 📅 ROLLOVER ALERT
     * Symbol:        ES
     * Current:       ESM5
     * Roll to:       ESU5
     * Days Until Roll: 4
     * Action Required: Roll open positions before expiration
     * </pre>
     */
    @Scheduled(cron = "${tradie.futures.rollover-check-cron:0 0 8 * * *}", zone = "UTC")
    public void publishRolloverAlerts() {
        List<RolloverAlert> alerts = checkForUpcomingRollovers(futuresProperties.getDefaultRolloverDays() * 2);
        if (alerts.isEmpty()) {
            log.debug("Rollover check: no upcoming rollovers within notification window");
            return;
        }
        for (RolloverAlert alert : alerts) {
            String message = String.format(
                    "ROLLOVER ALERT | Symbol: %s | Current: %s | Roll to: %s | Days Until Roll: %d | "
                            + "Action Required: Roll open positions before expiration",
                    alert.symbol(), alert.currentContract(), alert.nextContract(),
                    alert.daysRemaining());
            orderPublisher.publishSystemAlert("RolloverService", "ROLLOVER_ALERT", message);
            log.info("Rollover alert published: {} → {} in {} day(s)",
                    alert.currentContract(), alert.nextContract(), alert.daysRemaining());
        }
    }

    private String findNextContractSymbol(FuturesContract current) {
        return repository.findBySymbolAndActiveTrue(current.getSymbol()).stream()
                .filter(c -> !c.getFullSymbol().equals(current.getFullSymbol()))
                .filter(c -> c.getExpirationDate() != null
                        && c.getExpirationDate().isAfter(current.getExpirationDate()))
                .min(Comparator.comparing(FuturesContract::getExpirationDate))
                .map(FuturesContract::getFullSymbol)
                .orElse("UNKNOWN");
    }
}
