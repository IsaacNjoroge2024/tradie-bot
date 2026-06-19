package com.tradie.executor.controller;

import com.tradie.common.entity.TradeJournal;
import com.tradie.executor.dto.PerformanceStats;
import com.tradie.executor.dto.TradeJournalUpdateRequest;
import com.tradie.executor.journal.JournalAnalyticsService;
import com.tradie.executor.journal.TradeJournalService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/journal")
public class TradeJournalController {

    private final TradeJournalService journalService;
    private final JournalAnalyticsService analyticsService;

    public TradeJournalController(TradeJournalService journalService,
                                   JournalAnalyticsService analyticsService) {
        this.journalService = journalService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<Page<TradeJournal>> listEntries(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Double minPnl,
            @RequestParam(required = false) Double maxPnl,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                journalService.getEntries(symbol, strategy, startDate, endDate, minPnl, maxPnl, result, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradeJournal> getEntry(@PathVariable UUID id) {
        return journalService.getEntry(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TradeJournal> updateEntry(
            @PathVariable UUID id,
            @RequestBody TradeJournalUpdateRequest update) {
        try {
            return ResponseEntity.ok(journalService.updateEntry(id, update));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<PerformanceStats> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String strategy) {

        LocalDate from = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
        LocalDate to   = endDate   != null ? endDate   : LocalDate.now();
        if (from.isAfter(to)) {
            return ResponseEntity.<PerformanceStats>badRequest().build();
        }
        List<TradeJournal> entries = journalService.getByDateRange(from, to);

        if (strategy != null) {
            entries = entries.stream()
                    .filter(e -> strategy.equals(e.getStrategy()))
                    .toList();
        }

        return ResponseEntity.ok(analyticsService.calculateStats(entries));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String strategy) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return ResponseEntity.<String>badRequest().build();
        }
        List<TradeJournal> entries = journalService
                .getEntries(symbol, strategy, startDate, endDate, null, null, null, Pageable.unpaged())
                .getContent();

        String csv = journalService.exportToCsv(entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trade_journal.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
