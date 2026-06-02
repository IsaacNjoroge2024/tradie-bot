package com.tradie.executor.controller;

import com.tradie.executor.dto.ModifyStopRequest;
import com.tradie.executor.dto.ModifyTargetRequest;
import com.tradie.executor.dto.PositionCloseRequest;
import com.tradie.executor.dto.PositionDashboard;
import com.tradie.executor.dto.PositionSummary;
import com.tradie.executor.position.PositionDashboardService;
import com.tradie.executor.position.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for position management.
 *
 * <pre>
 * GET  /api/positions              – all open positions
 * GET  /api/positions/{id}         – position details
 * POST /api/positions/{id}/close   – manual close
 * PUT  /api/positions/{id}/stop    – modify stop loss
 * PUT  /api/positions/{id}/target  – modify take profit
 * GET  /api/positions/dashboard    – dashboard summary
 * </pre>
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private static final Logger log = LoggerFactory.getLogger(PositionController.class);

    private final PositionService positionService;
    private final PositionDashboardService dashboardService;

    public PositionController(PositionService positionService,
                              PositionDashboardService dashboardService) {
        this.positionService = positionService;
        this.dashboardService = dashboardService;
    }

    /** Returns all open positions as position summaries. */
    @GetMapping
    public List<PositionSummary> getOpenPositions() {
        return positionService.getOpenPositions().stream()
                .map(dashboardService::toSummary)
                .toList();
    }

    /** Returns dashboard summary with aggregated P&L across all open positions. */
    @GetMapping("/dashboard")
    public PositionDashboard getDashboard() {
        return dashboardService.getDashboard();
    }

    /** Returns position details for a given UUID. */
    @GetMapping("/{id}")
    public ResponseEntity<PositionSummary> getPosition(@PathVariable UUID id) {
        return positionService.getPosition(id)
                .map(dashboardService::toSummary)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Manually closes an open position via a MARKET order. */
    @PostMapping("/{id}/close")
    public ResponseEntity<String> closePosition(@PathVariable UUID id,
                                                @RequestBody(required = false) PositionCloseRequest request) {
        String reason = request != null && request.reason() != null ? request.reason() : "Manual close";
        try {
            positionService.closePosition(id, reason);
            return ResponseEntity.ok("Close order submitted for position " + id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to close position {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to submit close order");
        }
    }

    /** Modifies the stop loss price for an open position. */
    @PutMapping("/{id}/stop")
    public ResponseEntity<String> modifyStop(@PathVariable UUID id,
                                             @RequestBody ModifyStopRequest request) {
        if (request.stopPrice() == null) {
            return ResponseEntity.badRequest().body("stopPrice is required");
        }
        try {
            positionService.modifyStop(id, request.stopPrice());
            return ResponseEntity.ok("Stop loss updated to " + request.stopPrice());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to modify stop for position {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to modify stop order");
        }
    }

    /** Modifies the take profit price for an open position. */
    @PutMapping("/{id}/target")
    public ResponseEntity<String> modifyTarget(@PathVariable UUID id,
                                               @RequestBody ModifyTargetRequest request) {
        if (request.targetPrice() == null) {
            return ResponseEntity.badRequest().body("targetPrice is required");
        }
        try {
            positionService.modifyTarget(id, request.targetPrice());
            return ResponseEntity.ok("Take profit updated to " + request.targetPrice());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to modify target for position {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to modify take profit order");
        }
    }
}
