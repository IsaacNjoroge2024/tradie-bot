package com.tradie.strategy.service;

import com.tradie.common.entity.TradeSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class KillZoneServiceTest {

    private KillZoneService serviceAt(LocalTime time, boolean strict) {
        return new KillZoneService(strict) {
            @Override
            protected LocalTime getNow() {
                return time;
            }
        };
    }

    private TradeSignal signal(Double confidence) {
        TradeSignal s = new TradeSignal();
        s.setSymbol("AAPL");
        s.setAction(TradeSignal.SignalAction.BUY);
        s.setPrice(BigDecimal.valueOf(150));
        s.setConfidenceScore(confidence);
        return s;
    }

    @Test
    void londonOpen_isAllowed() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(3, 30), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
        assertNull(result.reason());
    }

    @Test
    void nyOpen_isAllowed() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(9, 0), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
    }

    @Test
    void silverBullet_isAllowed() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(10, 30), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
    }

    @Test
    void nyAfternoon_isAllowed() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(14, 0), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
    }

    @Test
    void asianSession_isAllowed() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(21, 0), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
    }

    @Test
    void outsideKillZone_highConfidence_allowedWithWarning() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(7, 0), false)
                .validate(signal(0.85));
        assertTrue(result.allowed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("high-confidence"));
    }

    @Test
    void outsideKillZone_lowConfidence_rejected() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(7, 0), false)
                .validate(signal(0.5));
        assertFalse(result.allowed());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("Outside Kill Zone"));
    }

    @Test
    void outsideKillZone_nullConfidence_rejected() {
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(7, 0), false)
                .validate(signal(null));
        assertFalse(result.allowed());
    }

    @Test
    void strictMode_outsideKillZone_alwaysRejected() {
        // High confidence but strict mode → still rejected
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(7, 0), true)
                .validate(signal(0.95));
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("strict mode"));
    }

    @Test
    void atZoneBoundaryStart_isIncluded() {
        // NY Open starts at 08:30
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(8, 30), false)
                .validate(signal(0.5));
        assertTrue(result.allowed());
    }

    @Test
    void atZoneBoundaryEnd_isExcluded() {
        // NY Open ends at 11:00 (exclusive)
        KillZoneService.KillZoneResult result = serviceAt(LocalTime.of(11, 0), false)
                .validate(signal(0.5));
        // 11:00 is Silver Bullet end too — outside both NY Open and Silver Bullet
        // NY Afternoon starts at 13:00, so 11:00 is outside all zones
        assertFalse(result.allowed());
    }
}
