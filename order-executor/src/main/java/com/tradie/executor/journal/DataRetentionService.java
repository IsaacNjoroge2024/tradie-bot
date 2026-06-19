package com.tradie.executor.journal;

import com.tradie.common.repository.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final AuditLogRepository auditLogRepository;

    @Value("${tradie.audit.retention-days:365}")
    private int retentionDays;

    public DataRetentionService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PostConstruct
    void validateRetentionDays() {
        if (retentionDays < 1) {
            throw new IllegalArgumentException(
                    "tradie.audit.retention-days must be >= 1, was: " + retentionDays);
        }
    }

    @Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
    public void purgeOldAuditLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("Data retention: purged {} audit log entries older than {} days", deleted, retentionDays);
    }
}
