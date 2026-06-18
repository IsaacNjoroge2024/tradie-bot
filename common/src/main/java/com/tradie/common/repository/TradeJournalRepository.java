package com.tradie.common.repository;

import com.tradie.common.entity.TradeJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeJournalRepository extends JpaRepository<TradeJournal, UUID> {

    List<TradeJournal> findBySymbol(String symbol);

    List<TradeJournal> findByStrategy(String strategy);

    List<TradeJournal> findByEntryTimeBetween(Instant start, Instant end);

    Optional<TradeJournal> findByPositionId(UUID positionId);

    @Query("SELECT j FROM TradeJournal j WHERE " +
           "(:symbol IS NULL OR j.symbol = :symbol) AND " +
           "(:strategy IS NULL OR j.strategy = :strategy) AND " +
           "(:startDate IS NULL OR j.entryTime >= :startDate) AND " +
           "(:endDate IS NULL OR j.entryTime <= :endDate) AND " +
           "(:minPnl IS NULL OR j.realizedPnl >= :minPnl) AND " +
           "(:maxPnl IS NULL OR j.realizedPnl <= :maxPnl) " +
           "ORDER BY j.entryTime DESC")
    Page<TradeJournal> findByFilters(
            @Param("symbol") String symbol,
            @Param("strategy") String strategy,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("minPnl") Double minPnl,
            @Param("maxPnl") Double maxPnl,
            Pageable pageable);
}
