package com.sentinelx.detection.rule.auth;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a subject accumulates too many failed logins within a short
 * window — the classic credential-stuffing / brute-force signature.
 */
@Component
public class FailedLoginSpikeRule implements DetectionRule {

    public static final String RULE_ID = "FAILED_LOGIN_SPIKE";
    static final Duration WINDOW = Duration.ofMinutes(5);
    static final int THRESHOLD = 5;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.auth");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        if (!"LOGIN_FAILED".equals(ctx.eventType())) {
            return null;
        }
        long failures = ctx.countInWindow(ctx.subjectScope(), WINDOW,
                e -> "security.auth".equals(e.get("topic")) && "LOGIN_FAILED".equals(e.get("eventType")));
        if (failures < THRESHOLD) {
            return null;
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 35,
                failures + " failed logins for '" + ctx.subjectKey() + "' within "
                        + WINDOW.toMinutes() + " minutes",
                "Lock the account and require step-up authentication before the next attempt");
    }
}
