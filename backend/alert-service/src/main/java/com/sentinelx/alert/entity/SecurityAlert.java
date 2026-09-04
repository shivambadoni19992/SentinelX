package com.sentinelx.alert.entity;

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

/** A security alert raised for review (schema alerts.security_alerts). */
@Entity
@Table(name = "security_alerts", schema = "alerts",
        indexes = {
                @Index(name = "idx_security_alerts_severity", columnList = "severity"),
                @Index(name = "idx_security_alerts_status", columnList = "status"),
                @Index(name = "idx_security_alerts_triggered", columnList = "triggered_at")
        })
public class SecurityAlert extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "severity", nullable = false, length = 64)
    private String severity;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "OPEN";

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt = Instant.now();

    /** Last response action applied to this alert (nullable). */
    @Column(name = "action", length = 64)
    private String action;

    /** Analyst or system principal that applied the action. */
    @Column(name = "actor", length = 128)
    private String actor;

    /** Structured detail of the applied action (downstream state changes). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_detail")
    private Map<String, Object> actionDetail = new HashMap<>();

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
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

    public Map<String, Object> getActionDetail() {
        return actionDetail;
    }

    public void setActionDetail(Map<String, Object> actionDetail) {
        this.actionDetail = actionDetail == null ? new HashMap<>() : actionDetail;
    }
}