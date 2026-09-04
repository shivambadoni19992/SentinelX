package com.sentinelx.detection.rule.api;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when one client floods the API — request volume per source IP far
 * beyond any legitimate interactive traffic.
 */
@Component
public class ApiRequestSpikeRule implements DetectionRule {

    public static final String RULE_ID = "API_REQUEST_SPIKE";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int THRESHOLD = 100;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.api");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        long count = ctx.countInWindow(ctx.ipScope(), WINDOW,
                e -> "security.api".equals(e.get("topic")));
        if (count < THRESHOLD) {
            return null;
        }
        String client = ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey();
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20,
                count + " API requests from " + client + " within "
                        + WINDOW.toSeconds() + " seconds",
                "Throttle the client at the gateway and inspect its access patterns");
    }
}
