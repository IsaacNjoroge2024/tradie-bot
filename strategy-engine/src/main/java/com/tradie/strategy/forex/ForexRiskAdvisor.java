package com.tradie.strategy.forex;

import com.tradie.strategy.config.ForexProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates forex-specific risk warnings for trades: weekend gaps, high spreads,
 * and low-liquidity session alerts.
 */
@Service
public class ForexRiskAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ForexRiskAdvisor.class);

    // Friday 22:00 UTC is the weekly forex market close
    private static final int FRIDAY_WARNING_HOUR = 18;

    private final ForexSessionService sessionService;
    private final ForexProperties forexProperties;

    public ForexRiskAdvisor(ForexSessionService sessionService, ForexProperties forexProperties) {
        this.sessionService = sessionService;
        this.forexProperties = forexProperties;
    }

    /**
     * Returns a list of risk warnings applicable to a forex trade.
     *
     * @param pair          currency pair symbol
     * @param spreadPips    current bid-ask spread in pips
     * @param time          signal time (UTC)
     * @param isNewPosition true when opening a new position
     * @return list of human-readable warning messages (may be empty)
     */
    public List<String> getWarnings(String pair, double spreadPips, Instant time,
                                     boolean isNewPosition) {
        List<String> warnings = new ArrayList<>();

        if (forexProperties.getRisk().isWeekendWarning() && isWeekendApproaching(time)) {
            warnings.add("Weekend gap risk: consider closing forex positions before Friday 22:00 UTC");
            log.warn("Weekend gap risk warning for pair: {}", pair);
        }

        if (spreadPips > forexProperties.getRisk().getMaxSpreadPips()) {
            warnings.add(String.format(
                    "High spread detected: %.1f pips (max recommended: %.1f)",
                    spreadPips, forexProperties.getRisk().getMaxSpreadPips()));
        }

        if (isNewPosition && forexProperties.getSessions().isWarnLowLiquidity()
                && sessionService.isLowLiquidity(time)) {
            warnings.add("Low liquidity period: no major trading sessions active");
        }

        if (isNewPosition && forexProperties.getSessions().isPreferOverlap()
                && !sessionService.isHighLiquidityPeriod(time)) {
            warnings.add("Outside London-NY overlap (13:00–17:00 UTC): reduced liquidity");
        }

        return warnings;
    }

    /**
     * Returns true if the forex weekend market close is within 4 hours (Friday ≥ 18:00 UTC)
     * or if the market is already closed (Saturday/Sunday).
     */
    boolean isWeekendApproaching(Instant time) {
        ZonedDateTime utc = ZonedDateTime.ofInstant(time, ZoneOffset.UTC);
        DayOfWeek day = utc.getDayOfWeek();
        if (day == DayOfWeek.FRIDAY && utc.getHour() >= FRIDAY_WARNING_HOUR) {
            return true;
        }
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
