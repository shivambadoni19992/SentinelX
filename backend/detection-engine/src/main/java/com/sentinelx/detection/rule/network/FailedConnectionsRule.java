package com.sentinelx.detection.rule.network;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a source generates an abnormal rate of failed/unreachable
 * connection attempts — service probing, outage hammering or a misbehaving
 * client. Severity escalates with the failure volume:
 *
 * <ul>
 *   <li>20–99 failed connections in 60s — MEDIUM (riskContribution 20)</li>
 *   <li>100+ failed connections in 60s — HIGH (riskContribution 35)</li>
 * </ul>
 */
@Component
public class FailedConnectionsRule implements DetectionRule {

    public static final String RULE_ID = "FAILED_CONNECTIONS";
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final int THRESHOLD = 20;
    static final int HIGH_THRESHOLD = 100;

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
        long failed = ctx.countInWindow(ctx.ipScope(), WINDOW,
                e -> "security.network".equals(e.get("topic"))
                        && ("DENIED".equals(e.get("outcome")) || "FAILURE".equals(e.get("outcome"))));
        if (failed < THRESHOLD) {
            return null;
        }
        String client = ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey();
        if (failed >= HIGH_THRESHOLD) {
            return new DetectionResult(RULE_ID, Severity.HIGH, 35,
                    failed + " failed connections from " + client + " within "
                            + WINDOW.toSeconds() + " seconds — sustained unreachable-host probing",
                    "BLOCK_CONNECTIONS — firewall the source and verify target service health");
        }
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20,
                failed + " failed connections from " + client + " within "
                        + WINDOW.toSeconds() + " seconds",
                "RATE_LIMIT connections from the source and inspect the failing target");
    }
}