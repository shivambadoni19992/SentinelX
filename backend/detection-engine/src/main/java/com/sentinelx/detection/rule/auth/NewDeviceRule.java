package com.sentinelx.detection.rule.auth;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on the first successful login from a device the subject has never
 * used before. The rule registers the device id itself, so it stays
 * self-contained: evaluate → check registry → remember.
 */
@Component
public class NewDeviceRule implements DetectionRule {

    public static final String RULE_ID = "NEW_DEVICE";
    private static final String SCOPE_PREFIX = "devices:";

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
        String deviceId = ctx.deviceId();
        if (deviceId == null) {
            return null;
        }
        String scope = SCOPE_PREFIX + ctx.subjectKey();
        if (ctx.windows().seenBefore(scope, deviceId)) {
            return null;
        }
        ctx.windows().remember(scope, deviceId);
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20,
                "First successful login for '" + ctx.subjectKey() + "' from unregistered device " + deviceId,
                "Verify device ownership with the user and enroll the device fingerprint");
    }
}
