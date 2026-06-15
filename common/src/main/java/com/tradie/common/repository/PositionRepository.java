package com.tradie.common.repository;

import com.tradie.common.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    long countByStatus(Position.PositionStatus status);

    List<Position> findByStatus(Position.PositionStatus status);

    List<Position> findByStatusAndSymbol(Position.PositionStatus status, String symbol);

    List<Position> findByStatusAndClosedAtAfter(Position.PositionStatus status, Instant closedAt);

    List<Position> findByStatusAndClosedAtGreaterThanEqual(Position.PositionStatus status, Instant closedAt);

    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FILTER (WHERE realized_pnl > 0), " +
            "COUNT(*) FILTER (WHERE realized_pnl < 0), " +
            "COALESCE(AVG(realized_pnl) FILTER (WHERE realized_pnl > 0), 0), " +
            "COALESCE(AVG(ABS(realized_pnl)) FILTER (WHERE realized_pnl < 0), 0) " +
            "FROM positions " +
            "WHERE strategy = :strategy AND status = 'CLOSED' AND closed_at > :cutoffTime")
    List<Object[]> findStrategyKellyStats(@Param("strategy") String strategy,
                                          @Param("cutoffTime") Instant cutoffTime);
}
