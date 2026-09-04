package com.sentinelx.detection.rule.auth;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on the first successful login from an IP address the subject has
 * never been seen on — a common account-takeover indicator.
 */
@Component
public class NewIpRule implements DetectionRule {

    public static final String RULE_ID = "NEW_IP";
    private static final String SCOPE_PREFIX = "ips:";

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
        if (!"LOGIN_SUCCESS".equals(ctx.eventType())) {
            return null;
        }
        String ip = ctx.sourceIp();
        if (ip == null) {
            return null;
        }
        String scope = SCOPE_PREFIX + ctx.subjectKey();
        if (ctx.windows().seenBefore(scope, ip)) {
            return null;
        }
        ctx.windows().remember(scope, ip);
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 15,
                "First successful login for '" + ctx.subjectKey() + "' from previously unseen IP " + ip,
                "Confirm login legitimacy with the user and enforce MFA for new locations");
    }
}
