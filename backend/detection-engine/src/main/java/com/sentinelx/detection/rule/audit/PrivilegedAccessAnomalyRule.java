package com.sentinelx.detection.rule.audit;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on privileged role/permission changes, escalated to CRITICAL when the
 * change happens outside business hours — a classic insider / APT signal.
 */
@Component
public class PrivilegedAccessAnomalyRule implements DetectionRule {

    public static final String RULE_ID = "PRIVILEGED_ACCESS_ANOMALY";
    private static final Set<String> PRIVILEGED_ACTIONS = Set.of(
            "PRIVILEGE_ESCALATION", "ROLE_CHANGE", "ROLE_ASSIGNED", "ADMIN_GRANTED",
            "PERMISSION_GRANTED", "ACCESS_GRANT", "PRIVILEGED_ACCESS");

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.audit", "security.auth");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        String action = ctx.eventType().toUpperCase();
        boolean privileged = PRIVILEGED_ACTIONS.contains(action)
                || action.contains("PRIVILEGE") || action.contains("ROLE_CHANGE")
                || action.contains("ADMIN_GRANT");
        if (!privileged) {
            return null;
        }
        int hour = ctx.occurredAt().atZone(java.time.ZoneId.systemDefault()).getHour();
        boolean offHours = hour < 7 || hour >= 20;
        String reason = "privileged action '" + ctx.eventType() + "' by '" + ctx.subjectKey() + "'"
                + (offHours ? " outside business hours (" + hour + ":00 local)" : "");
        return new DetectionResult(RULE_ID, offHours ? Severity.CRITICAL : Severity.HIGH,
                offHours ? 45 : 35, reason,
                "Require dual authorization and review privileged role assignments");
    }
}
