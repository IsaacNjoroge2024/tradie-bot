package com.tradie.common.repository;

import com.tradie.common.entity.AuditLog;
import com.tradie.common.entity.AuditLogId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLogId>,
        JpaSpecificationExecutor<AuditLog> {

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.id.time < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
