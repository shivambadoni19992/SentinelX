package com.sentinelx.detection.rule.network;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when one source touches many distinct destination ports in a short
 * window — reconnaissance / port scanning.
 */
@Component
public class PortScanRule implements DetectionRule {

    public static final String RULE_ID = "PORT_SCAN";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int DISTINCT_PORT_THRESHOLD = 10;

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
        int distinctPorts = ctx.distinctInWindow(ctx.ipScope(), WINDOW, "destinationPort",
                e -> "security.network".equals(e.get("topic"))).size();
        if (distinctPorts < DISTINCT_PORT_THRESHOLD) {
            return null;
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 35,
                distinctPorts + " distinct destination ports probed by "
                        + (ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey())
                        + " within " + WINDOW.toMinutes() + " minute(s)",
                "Block the source IP at the perimeter firewall and start a network investigation");
    }
}
