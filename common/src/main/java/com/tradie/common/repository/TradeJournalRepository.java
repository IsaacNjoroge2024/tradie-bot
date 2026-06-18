package com.tradie.common.repository;

import com.tradie.common.entity.TradeJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeJournalRepository extends JpaRepository<TradeJournal, UUID>,
        JpaSpecificationExecutor<TradeJournal> {

    List<TradeJournal> findBySymbol(String symbol);

    List<TradeJournal> findByStrategy(String strategy);

    List<TradeJournal> findByEntryTimeBetween(Instant start, Instant end);

    Optional<TradeJournal> findByPositionId(UUID positionId);
}
