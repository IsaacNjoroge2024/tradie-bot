package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KillZoneService {

    private static final Logger log = LoggerFactory.getLogger(KillZoneService.class);

    private static final ZoneId EST = ZoneId.of("America/New_York");

    // Each entry: name -> {startHour, startMin, endHour, endMin}
    // endHour=0 && endMin=0 means the session ends at midnight
    private static final Map<String, int[]> KILL_ZONES = new LinkedHashMap<>();

    static {
        KILL_ZONES.put("London Open",   new int[]{2,  0,  5,  0});
        KILL_ZONES.put("NY Open",       new int[]{8,  30, 11, 0});
        KILL_ZONES.put("Silver Bullet", new int[]{10, 0,  11, 0});
        KILL_ZONES.put("NY Afternoon",  new int[]{13, 0,  15, 0});
        KILL_ZONES.put("Asian Session", new int[]{20, 0,  0,  0});
    }

    private final boolean strictMode;

    public KillZoneService(@Value("${tradie.strategy.kill-zone-strict-mode:false}") boolean strictMode) {
        this.strictMode = strictMode;
    }

    public record KillZoneResult(boolean allowed, String reason, String warning) {}

    public KillZoneResult validate(TradeSignal signal) {
        LocalTime now = getNow();

        for (Map.Entry<String, int[]> zone : KILL_ZONES.entrySet()) {
            int[] t = zone.getValue();
            if (isInZone(now, t[0], t[1], t[2], t[3])) {
                log.debug("Signal {} is inside kill zone: {}", signal.getId(), zone.getKey());
                return new KillZoneResult(true, null, null);
            }
        }

        // Outside all kill zones
        if (strictMode) {
            return new KillZoneResult(false, "Outside Kill Zone - strict mode enabled", null);
        }

        Double confidence = signal.getConfidenceScore();
        if (confidence != null && confidence > 0.7) {
            String warning = String.format(
                    "Outside Kill Zone - allowed for high-confidence signal (%.0f%%)", confidence * 100);
            log.debug("Signal {} outside kill zone but allowed: {}", signal.getId(), warning);
            return new KillZoneResult(true, null, warning);
        }

        return new KillZoneResult(false,
                "Outside Kill Zone - medium/low confidence signal rejected", null);
    }

    // Protected to allow time injection in unit tests
    protected LocalTime getNow() {
        return LocalTime.now(EST);
    }

    private boolean isInZone(LocalTime now, int startH, int startM, int endH, int endM) {
        int start = startH * 60 + startM;
        int end   = endH   * 60 + endM;
        int curr  = now.getHour() * 60 + now.getMinute();

        if (end == 0) {
            // Midnight boundary (Asian Session 20:00 - 00:00)
            return curr >= start;
        }
        return curr >= start && curr < end;
    }
}
