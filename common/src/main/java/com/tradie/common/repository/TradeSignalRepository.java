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
    List<TradeSignal> findByStatusAndCreatedAtAfterOrderByCreatedAtAsc(
            @Param("status") TradeSignal.SignalStatus status,
            @Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(ts) FROM TradeSignal ts " +
            "WHERE ts.status = :status AND ts.createdAt >= :from")
    long countByStatusSince(
            @Param("status") TradeSignal.SignalStatus status,
            @Param("from") Instant from);
}
