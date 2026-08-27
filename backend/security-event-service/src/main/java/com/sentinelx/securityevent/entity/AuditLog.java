package com.sentinelx.securityevent.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Immutable-ish audit trail entry (schema events.audit_logs). */
@Entity
@Table(name = "audit_logs", schema = "events",
        indexes = {
                @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
                @Index(name = "idx_audit_logs_resource_type", columnList = "resource_type"),
                @Index(name = "idx_audit_logs_occurred_at", columnList = "occurred_at")
        })
public class AuditLog extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", nullable = false, length = 255)
    private String action;

    @Column(name = "actor")
    private String actor;

    @Column(name = "resource_type", length = 128)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "result", length = 64)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}