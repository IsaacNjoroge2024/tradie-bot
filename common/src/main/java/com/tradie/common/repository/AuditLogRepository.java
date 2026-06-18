package com.tradie.common.repository;

import com.tradie.common.entity.AuditLog;
import com.tradie.common.entity.AuditLogId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLogId> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:service IS NULL OR a.service = :service) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:startTime IS NULL OR a.id.time >= :startTime) AND " +
           "(:endTime IS NULL OR a.id.time <= :endTime) " +
           "ORDER BY a.id.time DESC")
    Page<AuditLog> findByFilters(
            @Param("service") String service,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.id.time < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
