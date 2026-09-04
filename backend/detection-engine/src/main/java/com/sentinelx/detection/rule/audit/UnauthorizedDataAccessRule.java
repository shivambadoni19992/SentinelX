package com.sentinelx.detection.rule.audit;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on denied / unauthorized access to protected resources, and on failed
 * data-export attempts — potential privilege abuse or exfiltration probing.
 */
@Component
public class UnauthorizedDataAccessRule implements DetectionRule {

    public static final String RULE_ID = "UNAUTHORIZED_DATA_ACCESS";

    private static final Set<String> DENIED = Set.of("DENIED", "UNAUTHORIZED", "FORBIDDEN", "401", "403");
    private static final Set<String> DATA_ACTIONS = Set.of("DATA_EXPORT", "DATA_ACCESS", "DATA_DOWNLOAD", "EXPORT");

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.audit", "security.api");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        String outcome = ctx.text("outcome", "status");
        String action = ctx.eventType();
        boolean denied = outcome != null && DENIED.contains(outcome.toUpperCase());
        boolean dataAction = DATA_ACTIONS.contains(action.toUpperCase())
                || DATA_ACTIONS.stream().anyMatch(action.toUpperCase()::contains);
        boolean match = denied || (dataAction && !"SUCCESS".equalsIgnoreCase(outcome));
        if (!match) {
            return null;
        }
        String reason = denied
                ? "access to '" + action + "' denied for '" + ctx.subjectKey() + "' (outcome=" + outcome + ")"
                : "data access action '" + action + "' did not succeed for '" + ctx.subjectKey()
                        + "' (outcome=" + outcome + ")";
        return new DetectionResult(RULE_ID, Severity.HIGH, 40, reason,
                "Revoke the credentials and investigate potential data exfiltration");
    }
}
