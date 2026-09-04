package com.sentinelx.detection.rule.network;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a source opens an abnormal number of connections in a short
 * window — connection-flood / DoS behaviour.
 */
@Component
public class ConnectionSpikeRule implements DetectionRule {

    public static final String RULE_ID = "CONNECTION_SPIKE";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int THRESHOLD = 50;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.network");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        long count = ctx.countInWindow(ctx.ipScope(), WINDOW,
                e -> "security.network".equals(e.get("topic")));
        if (count < THRESHOLD) {
            return null;
        }
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20,
                count + " network connections from "
                        + (ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey())
                        + " within " + WINDOW.toSeconds() + " seconds",
                "Rate-limit connections from the source and enable SYN-flood protection");
    }
}
