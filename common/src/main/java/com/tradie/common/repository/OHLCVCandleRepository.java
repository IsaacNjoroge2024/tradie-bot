package com.tradie.common.repository;

import com.tradie.common.entity.OHLCVCandle;
import com.tradie.common.entity.OHLCVId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OHLCVCandleRepository extends JpaRepository<OHLCVCandle, OHLCVId> {

    @Query("SELECT o FROM OHLCVCandle o " +
           "WHERE o.id.symbol = :symbol " +
           "AND o.id.exchange = :exchange " +
           "AND o.id.timeframe = :timeframe " +
           "AND o.id.time >= :since " +
           "ORDER BY o.id.time ASC")
    List<OHLCVCandle> findRecentBars(
            @Param("symbol") String symbol,
            @Param("exchange") String exchange,
            @Param("timeframe") String timeframe,
            @Param("since") Instant since);
}
