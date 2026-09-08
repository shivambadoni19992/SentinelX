package com.sentinelx.detection.rule.api;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a single source IP floods a specific endpoint — a targeted
 * API-abuse / application-DDoS pattern. Counts only requests to the same
 * path from the same IP within a short window:
 *
 * <ul>
 *   <li>30–59 requests to one endpoint in 10s — HIGH (riskContribution 35)</li>
 *   <li>60+ requests to one endpoint in 10s — CRITICAL (riskContribution 55)</li>
 * </ul>
 *
 * <p>Distinct from {@link ApiRequestSpikeRule} (which catches general per-IP
 * volume at 100/min regardless of endpoint) and {@link BotActivityRule}
 * (which catches user-agent / scanning patterns). The recommended action is
 * Redis rate-limiting so the gateway throttles the abusive client.
 */
@Component
public class ApiAbuseDetectedRule implements DetectionRule {

    public static final String RULE_ID = "API_ABUSE_DETECTED";
    static final Duration WINDOW = Duration.ofSeconds(10);
    static final int THRESHOLD = 30;
    static final int CRITICAL_THRESHOLD = 60;

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
        String path = ctx.text("path", "endpoint", "url");
        if (path == null || path.isBlank()) {
            return null;
        }
        String ip = ctx.sourceIp();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        // Scope by IP + endpoint so we detect targeted floods, not general load.
        String abuseScope = "abuse:" + ip + ":" + path;
        long count = ctx.countInWindow(abuseScope, WINDOW,
                e -> "security.api".equals(e.get("topic"))
                        && path.equals(e.get("path")));
        if (count < THRESHOLD) {
            return null;
        }
        if (count >= CRITICAL_THRESHOLD) {
            return new DetectionResult(RULE_ID, Severity.CRITICAL, 55,
                    count + " requests from " + ip + " to " + path + " within "
                            + WINDOW.toSeconds() + "s — targeted API abuse",
                    "RATE_LIMIT — apply Redis rate-limit for " + ip + " and review endpoint " + path);
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 35,
                count + " requests from " + ip + " to " + path + " within "
                        + WINDOW.toSeconds() + "s — elevated endpoint flood",
                "RATE_LIMIT — throttle " + ip + " at the gateway for endpoint " + path);
    }
}
