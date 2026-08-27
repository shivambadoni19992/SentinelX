package com.sentinelx.risk.entity;

import java.math.BigDecimal;
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

/** A scored risk decision for a subject (schema risk.risk_decisions). */
@Entity
@Table(name = "risk_decisions", schema = "risk",
        indexes = {
                @Index(name = "idx_risk_decisions_subject", columnList = "subject_type, subject_id"),
                @Index(name = "idx_risk_decisions_decision", columnList = "decision_at"),
                @Index(name = "idx_risk_decisions_risk_level", columnList = "risk_level")
        })
public class RiskDecision extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "rule_version", length = 64)
    private String ruleVersion;

    @Column(name = "risk_level", nullable = false, length = 64)
    private String riskLevel = "LOW";

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors")
    private Map<String, Object> factors = new HashMap<>();

    @Column(name = "action", nullable = false, length = 255)
    private String action = "ALLOW";

    @Column(name = "decision_at")
    private Instant decisionAt;

    public UUID getId() {
        return id;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public Map<String, Object> getFactors() {
        return factors;
    }

    public void setFactors(Map<String, Object> factors) {
        this.factors = factors;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(Instant decisionAt) {
        this.decisionAt = decisionAt;
    }
}