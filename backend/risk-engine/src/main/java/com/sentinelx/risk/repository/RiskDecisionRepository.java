package com.sentinelx.risk.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.risk.entity.RiskDecision;

public interface RiskDecisionRepository extends JpaRepository<RiskDecision, UUID> {

    List<RiskDecision> findBySubjectId(UUID subjectId);

    List<RiskDecision> findByRiskLevel(String riskLevel);
}