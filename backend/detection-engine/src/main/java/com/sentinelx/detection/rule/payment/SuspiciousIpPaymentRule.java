package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a payment originates from a suspicious or previously-unseen IP address.
 * Flags payments where the sourceIp is marked suspicious or the IP has not been
 * seen for this customer in the trailing window — a common card-testing / fraud indicator.
 */
@Component
public class SuspiciousIpPaymentRule implements DetectionRule {

    public static final String RULE_ID = "SUSPICIOUS_IP";
    private static final String SCOPE_PREFIX = "payment-ips:";
    private static final Duration WINDOW = Duration.ofHours(24);

    /** Known-bad IP ranges (anonymizers, Tor exit nodes, known fraud sources). */
    private static final Set<String> SUSPICIOUS_PREFIXES = Set.of(
            "10.99.", "192.168.255.", "172.16.99.", "203.0.113.", "198.51.100.");

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.payment");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        if (!"PAYMENT_CREATED".equals(ctx.eventType()) && !"PAYMENT_AUTHORIZED".equals(ctx.eventType())) {
            return null;
        }
        String ip = ctx.sourceIp();
        if (ip == null || ip.isBlank()) {
            return null;
        }

        // Check if the IP is explicitly flagged as suspicious in the payload
        com.fasterxml.jackson.databind.JsonNode suspiciousFlag = ctx.payload().path("suspiciousIp");
        boolean explicitlySuspicious = suspiciousFlag.asBoolean(false) || "true".equalsIgnoreCase(suspiciousFlag.asText(""));

        // Check if the IP matches a known-bad prefix
        boolean matchesBadPrefix = SUSPICIOUS_PREFIXES.stream().anyMatch(ip::startsWith);

        // Check if this IP has been seen for this customer before
        String scope = SCOPE_PREFIX + ctx.subjectKey();
        boolean seenBefore = ctx.windows().seenBefore(scope, ip);
        ctx.windows().remember(scope, ip);

        if (!explicitlySuspicious && !matchesBadPrefix && seenBefore) {
            return null;
        }

        double amount = ctx.number("amount", "totalAmount");
        String reason;
        if (explicitlySuspicious) {
            reason = String.format("Payment of %.2f from explicitly flagged suspicious IP '%s' for customer '%s'",
                    amount, ip, ctx.subjectKey());
        } else if (matchesBadPrefix) {
            reason = String.format("Payment of %.2f from known-bad IP range '%s' for customer '%s'",
                    amount, ip, ctx.subjectKey());
        } else {
            reason = String.format("Payment of %.2f from new IP '%s' for customer '%s' — IP not seen in last %d hours",
                    amount, ip, ctx.subjectKey(), WINDOW.toHours());
        }

        return new DetectionResult(RULE_ID, Severity.HIGH, 30, reason,
                "Hold payment for IP reputation review and verify customer identity");
    }
}
