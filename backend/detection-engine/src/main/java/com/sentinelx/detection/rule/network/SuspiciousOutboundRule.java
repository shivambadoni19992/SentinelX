package com.sentinelx.detection.rule.network;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on large-volume outbound transfers from a host — data staging or
 * exfiltration towards an external destination.
 */
@Component
public class SuspiciousOutboundRule implements DetectionRule {

    public static final String RULE_ID = "SUSPICIOUS_OUTBOUND";
    static final double BYTE_THRESHOLD = 1_000_000_000d; // 1 GiB

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
        String direction = ctx.text("direction", "transferDirection");
        boolean outbound = "outbound".equalsIgnoreCase(direction)
                || ctx.eventType().toUpperCase().contains("OUTBOUND");
        if (!outbound) {
            return null;
        }
        double bytes = ctx.number("bytes", "bytesTransferred", "sizeBytes");
        boolean external = "true".equalsIgnoreCase(
                ctx.text("destinationExternal", "external") == null ? "false"
                        : ctx.text("destinationExternal", "external"));
        if (bytes < BYTE_THRESHOLD && !external) {
            return null;
        }
        String detail = bytes >= BYTE_THRESHOLD
                ? String.format("%.0f GB outbound transfer from %s", bytes / 1e9,
                        ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey())
                : "outbound transfer to an external destination from "
                        + (ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey());
        return new DetectionResult(RULE_ID, Severity.HIGH, 30, detail,
                "Quarantine the host and review egress firewall rules");
    }
}
