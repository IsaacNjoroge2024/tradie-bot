package com.tradie.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AuditLogId implements Serializable {

    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private Instant time;

    public AuditLogId() {}

    public AuditLogId(UUID id, Instant time) {
        this.id = id;
        this.time = time;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLogId other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(time, other.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, time);
    }
}
