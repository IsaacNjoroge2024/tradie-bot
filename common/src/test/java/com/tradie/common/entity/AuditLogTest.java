package com.tradie.common.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @Test
    void auditLogId_equality_basedOnIdAndTime() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.now();
        AuditLogId a = new AuditLogId(id, time);
        AuditLogId b = new AuditLogId(id, time);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void auditLogId_differentId_notEqual() {
        Instant time = Instant.now();
        AuditLogId a = new AuditLogId(UUID.randomUUID(), time);
        AuditLogId b = new AuditLogId(UUID.randomUUID(), time);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void auditLog_settersAndGetters() {
        AuditLog log = new AuditLog();
        AuditLogId id = new AuditLogId(UUID.randomUUID(), Instant.now());
        UUID entityId = UUID.randomUUID();

        log.setId(id);
        log.setService("api-gateway");
        log.setAction("SIGNAL_RECEIVED");
        log.setEntityType("SIGNAL");
        log.setEntityId(entityId);
        log.setDetails("{\"symbol\":\"AAPL\"}");
        log.setUserId("SYSTEM");

        assertThat(log.getId()).isEqualTo(id);
        assertThat(log.getService()).isEqualTo("api-gateway");
        assertThat(log.getAction()).isEqualTo("SIGNAL_RECEIVED");
        assertThat(log.getEntityType()).isEqualTo("SIGNAL");
        assertThat(log.getEntityId()).isEqualTo(entityId);
        assertThat(log.getDetails()).isEqualTo("{\"symbol\":\"AAPL\"}");
        assertThat(log.getUserId()).isEqualTo("SYSTEM");
    }

    @Test
    void auditLog_prePersist_setsIdAndTime() throws Exception {
        AuditLog log = new AuditLog();
        log.setService("test");
        log.setAction("TEST_ACTION");
        assertThat(log.getId()).isNull();

        // Invoke @PrePersist via reflection (JPA calls this before INSERT)
        var method = AuditLog.class.getDeclaredMethod("onCreate");
        method.setAccessible(true);
        method.invoke(log);

        assertThat(log.getId()).isNotNull();
        assertThat(log.getId().getId()).isNotNull();
        assertThat(log.getId().getTime()).isNotNull();
    }
}
