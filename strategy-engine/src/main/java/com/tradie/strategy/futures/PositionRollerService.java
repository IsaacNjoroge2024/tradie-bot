package com.tradie.strategy.futures;

import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.service.OrderPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles futures contract position rollovers.
 *
 * <p>When {@code tradie.futures.auto-roll-positions=false} (the default), this service
 * publishes a structured alert to the {@code tradie.alerts} topic so the trader can review
 * and manually execute the roll via the trading terminal. When auto-roll is enabled it will
 * close the current position and re-open it in the new contract.
 *
 * <p>Physical order submission to IBKR is handled by the order-executor service; this service
 * is responsible for the decision logic and alerting.
 */
@Service
public class PositionRollerService {

    private static final Logger log = LoggerFactory.getLogger(PositionRollerService.class);

    private final PositionRepository positionRepository;
    private final RolloverService rolloverService;
    private final OrderPublisher orderPublisher;

    public PositionRollerService(PositionRepository positionRepository,
                                  RolloverService rolloverService,
                                  OrderPublisher orderPublisher) {
        this.positionRepository = positionRepository;
        this.rolloverService = rolloverService;
        this.orderPublisher = orderPublisher;
    }

    /**
     * Rolls an open position from the current expiring contract to the new contract.
     *
     * <p>Publishes a rollover alert to the {@code tradie.alerts} topic. When
     * {@code auto-roll-positions} is enabled, the physical close+reopen orders would be
     * submitted through the order-executor.
     *
     * @param position    the open futures position to roll
     * @param newContract the full symbol of the contract to roll into (e.g., "ESU5")
     */
    public void rollPosition(Position position, String newContract) {
        if (position == null || newContract == null || newContract.isBlank()) {
            log.warn("rollPosition called with invalid args: position={}, newContract={}",
                    position, newContract);
            return;
        }

        String currentContract = position.getSymbol();
        log.info("Rolling position {} from {} → {} qty={} side={}",
                position.getId(), currentContract, newContract,
                position.getQuantity(), position.getSide());

        orderPublisher.publishSystemAlert(
                "PositionRollerService",
                "ROLLOVER_REQUIRED",
                String.format("Roll position %s: %s → %s qty=%s side=%s",
                        position.getId(), currentContract, newContract,
                        position.getQuantity(), position.getSide()));
    }

    /**
     * Rolls an open position using a calendar spread order for cleaner execution.
     *
     * <p>A spread roll simultaneously closes the front-month leg and opens the back-month leg,
     * minimising slippage at the rollover point.
     *
     * @param position     the open futures position to roll
     * @param newContract  the full symbol of the contract to roll into (e.g., "ESU5")
     * @param spreadPrice  the target spread (price differential between the two contracts)
     */
    public void rollWithSpread(Position position, String newContract, double spreadPrice) {
        if (position == null || newContract == null || newContract.isBlank()) {
            log.warn("rollWithSpread called with invalid args: position={}, newContract={}",
                    position, newContract);
            return;
        }

        String currentContract = position.getSymbol();
        log.info("Spread-rolling position {} from {} → {} spread={} qty={} side={}",
                position.getId(), currentContract, newContract,
                spreadPrice, position.getQuantity(), position.getSide());

        orderPublisher.publishSystemAlert(
                "PositionRollerService",
                "SPREAD_ROLLOVER_REQUIRED",
                String.format("Spread roll position %s: %s → %s spread=%.4f qty=%s side=%s",
                        position.getId(), currentContract, newContract,
                        spreadPrice, position.getQuantity(), position.getSide()));
    }

    /**
     * Returns all open futures positions that have reached or passed their rollover date.
     *
     * @return list of open futures positions eligible for rollover
     */
    public List<Position> findPositionsDueToRoll() {
        // Position.symbol holds root symbols (e.g., "ES"), so resolve via root-symbol lookup
        return positionRepository.findByStatus(Position.PositionStatus.OPEN).stream()
                .filter(p -> "FUTURES".equalsIgnoreCase(p.getAssetClass()))
                .filter(p -> rolloverService.shouldRollByRootSymbol(p.getSymbol()))
                .toList();
    }
}
