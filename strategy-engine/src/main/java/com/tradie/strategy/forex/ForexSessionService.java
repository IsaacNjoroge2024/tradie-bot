package com.tradie.strategy.forex;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks active forex trading sessions based on UTC time.
 *
 * <p>Session hours (UTC):
 * <ul>
 *   <li>Sydney:   22:00 – 07:00</li>
 *   <li>Tokyo:    00:00 – 09:00</li>
 *   <li>London:   08:00 – 17:00</li>
 *   <li>New York: 13:00 – 22:00</li>
 * </ul>
 * Highest liquidity occurs during the London-NY overlap: 13:00–17:00 UTC.
 */
@Service
public class ForexSessionService {

    public enum ForexSession { SYDNEY, TOKYO, LONDON, NEW_YORK }

    private static final int SYDNEY_OPEN   = 22;
    private static final int SYDNEY_CLOSE  = 7;
    private static final int TOKYO_OPEN    = 0;
    private static final int TOKYO_CLOSE   = 9;
    private static final int LONDON_OPEN   = 8;
    private static final int LONDON_CLOSE  = 17;
    private static final int NY_OPEN       = 13;
    private static final int NY_CLOSE      = 22;
    private static final int OVERLAP_OPEN  = 13;
    private static final int OVERLAP_CLOSE = 17;

    /**
     * Returns all active trading sessions for the given UTC instant.
     */
    public List<ForexSession> getActiveSessions(Instant time) {
        int hour = ZonedDateTime.ofInstant(time, ZoneOffset.UTC).getHour();
        List<ForexSession> sessions = new ArrayList<>();

        if (isInSession(hour, SYDNEY_OPEN, SYDNEY_CLOSE)) sessions.add(ForexSession.SYDNEY);
        if (isInSession(hour, TOKYO_OPEN, TOKYO_CLOSE))   sessions.add(ForexSession.TOKYO);
        if (isInSession(hour, LONDON_OPEN, LONDON_CLOSE)) sessions.add(ForexSession.LONDON);
        if (isInSession(hour, NY_OPEN, NY_CLOSE))          sessions.add(ForexSession.NEW_YORK);

        return sessions;
    }

    /**
     * Returns true during the London-NY overlap (13:00–17:00 UTC) — highest liquidity.
     */
    public boolean isHighLiquidityPeriod(Instant time) {
        int hour = ZonedDateTime.ofInstant(time, ZoneOffset.UTC).getHour();
        return hour >= OVERLAP_OPEN && hour < OVERLAP_CLOSE;
    }

    /**
     * Returns true when neither London nor New York is active.
     * These two sessions provide the majority of forex liquidity.
     */
    public boolean isLowLiquidity(Instant time) {
        List<ForexSession> sessions = getActiveSessions(time);
        return sessions.stream().noneMatch(s -> s == ForexSession.LONDON || s == ForexSession.NEW_YORK);
    }

    // Handles sessions that wrap midnight (e.g., Sydney 22:00-07:00)
    private boolean isInSession(int hour, int open, int close) {
        if (open <= close) {
            return hour >= open && hour < close;
        }
        return hour >= open || hour < close;
    }
}
