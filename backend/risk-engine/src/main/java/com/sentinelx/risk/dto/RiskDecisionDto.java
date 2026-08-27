package com.sentinelx.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.risk.entity.RiskDecision;

/** API contract for a RiskDecision — entities are never exposed directly. */
public record RiskDecisionDto(
        UUID id,
        UUID subjectId,
        String subjectType,
        String ruleVersion,
        String riskLevel,
        BigDecimal riskScore,
        Map<String, Object> factors,
        String action,
        Instant decisionAt,
        Instant createdAt,
        Instant updatedAt) {

    public static RiskDecisionDto from(RiskDecision d) {
        return new RiskDecisionDto(d.getId(), d.getSubjectId(), d.getSubjectType(),
                d.getRuleVersion(), d.getRiskLevel(), d.getRiskScore(), d.getFactors(),
                d.getAction(), d.getDecisionAt(), d.getCreatedAt(), d.getUpdatedAt());
    }

    public RiskDecision toEntity() {
        RiskDecision d = new RiskDecision();
        d.setSubjectId(subjectId);
        d.setSubjectType(subjectType);
        d.setRuleVersion(ruleVersion);
        d.setRiskLevel(riskLevel == null ? "LOW" : riskLevel);
        d.setRiskScore(riskScore);
        if (factors != null) {
            d.setFactors(factors);
        }
        d.setAction(action == null ? "ALLOW" : action);
        d.setDecisionAt(decisionAt);
        return d;
    }
}