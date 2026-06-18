package com.tradie.common.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @EmbeddedId
    private AuditLogId id;

    @Column(nullable = false, length = 50)
    private String service;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "user_id", length = 50)
    private String userId;

    public AuditLog() {}

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = new AuditLogId(UUID.randomUUID(), Instant.now());
        }
    }

    public AuditLogId getId() { return id; }
    public void setId(AuditLogId id) { this.id = id; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
