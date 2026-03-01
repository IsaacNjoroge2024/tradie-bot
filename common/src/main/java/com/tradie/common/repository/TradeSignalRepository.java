package com.tradie.common.repository;

import com.tradie.common.entity.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TradeSignalRepository extends JpaRepository<TradeSignal, UUID> {

    List<TradeSignal> findByStatusOrderByCreatedAtDesc(TradeSignal.SignalStatus status);

    List<TradeSignal> findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(
            String symbol, Instant since);

    @Query("SELECT ts FROM TradeSignal ts " +
            "WHERE ts.status = :status AND ts.createdAt > :cutoff " +
            "ORDER BY ts.createdAt ASC")
    List<TradeSignal> findPendingSignals(
            @Param("status") TradeSignal.SignalStatus status,
            @Param("cutoff") Instant cutoff);

    @Query(value = "SELECT COUNT(*) FROM trade_signals " +
            "WHERE status = 'EXECUTED' AND created_at >= CURRENT_DATE",
            nativeQuery = true)
    long countExecutedToday();
}
