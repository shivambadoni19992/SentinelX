package com.sentinelx.securityevent.entity;

import java.net.InetAddress;
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

/** A security-relevant event observed by the pipeline (schema events.security_events). */
@Entity
@Table(name = "security_events", schema = "events",
        indexes = {
                @Index(name = "idx_security_events_event_type", columnList = "event_type"),
                @Index(name = "idx_security_events_user_id", columnList = "user_id"),
                @Index(name = "idx_security_events_severity", columnList = "severity"),
                @Index(name = "idx_security_events_occurred", columnList = "occurred_at")
        })
public class SecurityEvent extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "actor")
    private String actor;

    @Column(name = "action", length = 255)
    private String action;

    @Column(name = "outcome", nullable = false, length = 64)
    private String outcome = "UNKNOWN";

    @Column(name = "severity", nullable = false, length = 64)
    private String severity = "LOW";

    @Column(name = "source_ip")
    private InetAddress sourceIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public InetAddress getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(InetAddress sourceIp) {
        this.sourceIp = sourceIp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}