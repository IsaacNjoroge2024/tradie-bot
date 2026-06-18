package com.tradie.executor.controller;

import com.tradie.common.entity.AuditLog;
import com.tradie.common.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (service != null)    predicates.add(cb.equal(root.get("service"), service));
            if (action != null)     predicates.add(cb.equal(root.get("action"), action));
            if (entityType != null) predicates.add(cb.equal(root.get("entityType"), entityType));
            if (startTime != null)  predicates.add(cb.greaterThanOrEqualTo(root.get("id").get("time"), startTime));
            if (endTime != null)    predicates.add(cb.lessThanOrEqualTo(root.get("id").get("time"), endTime));
            query.orderBy(cb.desc(root.get("id").get("time")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ResponseEntity.ok(auditLogRepository.findAll(spec, PageRequest.of(page, size)));
    }
}
