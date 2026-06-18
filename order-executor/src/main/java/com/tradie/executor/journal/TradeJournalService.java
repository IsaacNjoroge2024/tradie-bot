package com.tradie.executor.journal;

import com.tradie.common.entity.Position;
import com.tradie.common.entity.TradeJournal;
import com.tradie.common.repository.TradeJournalRepository;
import com.tradie.executor.dto.TradeJournalUpdateRequest;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TradeJournalService {

    private static final Logger log = LoggerFactory.getLogger(TradeJournalService.class);

    private final TradeJournalRepository tradeJournalRepository;

    @Value("${tradie.journal.auto-create:true}")
    private boolean autoCreate;

    public TradeJournalService(TradeJournalRepository tradeJournalRepository) {
        this.tradeJournalRepository = tradeJournalRepository;
    }

    @Transactional
    public TradeJournal createEntry(Position position) {
        if (!autoCreate) return null;

        Optional<TradeJournal> existing = tradeJournalRepository.findByPositionId(position.getId());
        if (existing.isPresent()) {
            log.debug("Journal entry already exists for position {}", position.getId());
            return existing.get();
        }

        TradeJournal entry = new TradeJournal();
        entry.setPositionId(position.getId());
        entry.setSymbol(position.getSymbol());
        entry.setSide(position.getSide() != null ? position.getSide().name() : null);
        entry.setEntryTime(position.getOpenedAt());
        entry.setExitTime(position.getClosedAt());
        entry.setEntryPrice(position.getEntryPrice() != null ? position.getEntryPrice().doubleValue() : null);
        entry.setExitPrice(position.getExitPrice() != null ? position.getExitPrice().doubleValue() : null);
        entry.setQuantity(position.getQuantity() != null ? position.getQuantity().doubleValue() : null);
        entry.setStrategy(position.getStrategy());

        if (position.getRealizedPnl() != null) {
            entry.setRealizedPnl(position.getRealizedPnl().doubleValue());
            entry.setRealizedPnlPct(calculatePnlPct(position));
        }

        if (position.getCommissionTotal() != null) {
            entry.setCommissions(position.getCommissionTotal().doubleValue());
        }

        TradeJournal saved = tradeJournalRepository.save(entry);
        log.info("Journal entry created for position {}: symbol={} pnl={}",
                position.getId(), position.getSymbol(), entry.getRealizedPnl());
        return saved;
    }

    @Transactional
    public TradeJournal updateEntry(UUID id, TradeJournalUpdateRequest update) {
        TradeJournal entry = tradeJournalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + id));

        if (update.notes() != null) entry.setNotes(update.notes());
        if (update.setupQuality() != null) entry.setSetupQuality(update.setupQuality());
        if (update.executionQuality() != null) entry.setExecutionQuality(update.executionQuality());
        if (update.tags() != null) entry.setTags(update.tags());
        if (update.marketCondition() != null) entry.setMarketCondition(update.marketCondition());
        if (update.newsContext() != null) entry.setNewsContext(update.newsContext());
        if (update.entryChartUrl() != null) entry.setEntryChartUrl(update.entryChartUrl());
        if (update.exitChartUrl() != null) entry.setExitChartUrl(update.exitChartUrl());

        return tradeJournalRepository.save(entry);
    }

    public Optional<TradeJournal> getEntry(UUID id) {
        return tradeJournalRepository.findById(id);
    }

    public Page<TradeJournal> getEntries(String symbol, String strategy,
                                          LocalDate startDate, LocalDate endDate,
                                          Double minPnl, Double maxPnl, String result,
                                          Pageable pageable) {
        Instant start = startDate != null ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant end   = endDate   != null ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Double resolvedMin = minPnl;
        Double resolvedMax = maxPnl;
        if ("WIN".equalsIgnoreCase(result) && resolvedMin == null)  resolvedMin = 0.0;
        if ("LOSS".equalsIgnoreCase(result) && resolvedMax == null) resolvedMax = 0.0;

        final Double finalMin = resolvedMin;
        final Double finalMax = resolvedMax;

        Specification<TradeJournal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (symbol != null)    predicates.add(cb.equal(root.get("symbol"), symbol));
            if (strategy != null)  predicates.add(cb.equal(root.get("strategy"), strategy));
            if (start != null)     predicates.add(cb.greaterThanOrEqualTo(root.get("entryTime"), start));
            if (end != null)       predicates.add(cb.lessThanOrEqualTo(root.get("entryTime"), end));
            if (finalMin != null)  predicates.add(cb.greaterThanOrEqualTo(root.get("realizedPnl"), finalMin));
            if (finalMax != null)  predicates.add(cb.lessThanOrEqualTo(root.get("realizedPnl"), finalMax));
            query.orderBy(cb.desc(root.get("entryTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return tradeJournalRepository.findAll(spec, pageable);
    }

    public List<TradeJournal> getByDateRange(LocalDate start, LocalDate end) {
        return tradeJournalRepository.findByEntryTimeBetween(
                start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public List<TradeJournal> getByStrategy(String strategy) {
        return tradeJournalRepository.findByStrategy(strategy);
    }

    public List<TradeJournal> getBySymbol(String symbol) {
        return tradeJournalRepository.findBySymbol(symbol);
    }

    public String exportToCsv(List<TradeJournal> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,symbol,side,entryTime,exitTime,entryPrice,exitPrice,quantity,"
                + "realizedPnl,realizedPnlPct,commissions,strategy,setupQuality,executionQuality,notes\n");
        for (TradeJournal e : entries) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,\"%s\"\n",
                    e.getId(),
                    nvl(e.getSymbol()),
                    nvl(e.getSide()),
                    nvl(e.getEntryTime()),
                    nvl(e.getExitTime()),
                    nvl(e.getEntryPrice()),
                    nvl(e.getExitPrice()),
                    nvl(e.getQuantity()),
                    nvl(e.getRealizedPnl()),
                    nvl(e.getRealizedPnlPct()),
                    nvl(e.getCommissions()),
                    nvl(e.getStrategy()),
                    nvl(e.getSetupQuality()),
                    nvl(e.getExecutionQuality()),
                    e.getNotes() != null ? e.getNotes().replace("\"", "\"\"") : ""));
        }
        return csv.toString();
    }

    private double calculatePnlPct(Position position) {
        if (position.getEntryPrice() == null || position.getQuantity() == null
                || position.getRealizedPnl() == null) return 0.0;
        BigDecimal entryValue = position.getEntryPrice().multiply(position.getQuantity());
        if (entryValue.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return position.getRealizedPnl()
                .divide(entryValue, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private String nvl(Object value) {
        return value != null ? value.toString() : "";
    }
}
