package com.sentinelx.simulation.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * A simulation run (schema {@code simulator.simulation_runs}). The run only
 * tracks what it generated and what the downstream pipeline produced — the
 * run never creates alerts itself.
 */
@Entity
@Table(name = "simulation_runs", schema = "simulator",
        indexes = {
                @Index(name = "idx_simulation_runs_status", columnList = "status"),
                @Index(name = "idx_simulation_runs_started_at", columnList = "started_at")
        })
public class SimulationRun extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly label; optional. */
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    /** Legacy Phase 1 column, retained for provenance; superseded by {@code type}. */
    @Column(name = "scenario", length = 128)
    private String scenario;

    /** The {@link com.sentinelx.simulation.domain.SimulationType} being simulated. */
    @Column(name = "type", length = 64)
    private String type;

    /** Simulation parameters (see SimulationConfig.toMap()). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private Map<String, Object> configuration = new LinkedHashMap<>();

    /** {@link com.sentinelx.simulation.domain.SimulationStatus} name. */
    @Column(name = "status", nullable = false, length = 64)
    private String status = "QUEUED";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "events_generated", nullable = false)
    private long eventsGenerated;

    @Column(name = "events_processed", nullable = false)
    private long eventsProcessed;

    @Column(name = "detections", nullable = false)
    private long detections;

    @Column(name = "risk_decisions", nullable = false)
    private long riskDecisions;

    @Column(name = "alerts", nullable = false)
    private long alerts;

    @Column(name = "actions", nullable = false)
    private long actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false)
    private List<String> errors = new ArrayList<>();

    @Column(name = "run_by", length = 128)
    private String runBy;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public long getEventsGenerated() {
        return eventsGenerated;
    }

    public void setEventsGenerated(long eventsGenerated) {
        this.eventsGenerated = eventsGenerated;
    }

    public long getEventsProcessed() {
        return eventsProcessed;
    }

    public void setEventsProcessed(long eventsProcessed) {
        this.eventsProcessed = eventsProcessed;
    }

    public long getDetections() {
        return detections;
    }

    public void setDetections(long detections) {
        this.detections = detections;
    }

    public long getRiskDecisions() {
        return riskDecisions;
    }

    public void setRiskDecisions(long riskDecisions) {
        this.riskDecisions = riskDecisions;
    }

    public long getAlerts() {
        return alerts;
    }

    public void setAlerts(long alerts) {
        this.alerts = alerts;
    }

    public long getActions() {
        return actions;
    }

    public void setActions(long actions) {
        this.actions = actions;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public String getRunBy() {
        return runBy;
    }

    public void setRunBy(String runBy) {
        this.runBy = runBy;
    }
}
