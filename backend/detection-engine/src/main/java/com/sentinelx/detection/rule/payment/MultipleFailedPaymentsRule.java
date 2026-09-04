package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a customer racks up repeated failed/declined payments — stolen
 * card cycling or balance-probing behaviour.
 */
@Component
public class MultipleFailedPaymentsRule implements DetectionRule {

    public static final String RULE_ID = "MULTIPLE_FAILED_PAYMENTS";
    static final Duration WINDOW = Duration.ofMinutes(5);
    static final int THRESHOLD = 3;

    private static final Set<String> FAILED_STATUSES = Set.of(
            "DECLINED", "FAILED", "FAILURE", "ERROR", "REJECTED", "BLOCKED");

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
        String status = ctx.text("status", "outcome");
        boolean failed = status != null && FAILED_STATUSES.contains(status.toUpperCase());
        // The PAYMENT_CREATED event of a declined payment is itself the failure
        // signal; also accept explicit PAYMENT_FAILED event types.
        if (!failed && !"PAYMENT_FAILED".equals(ctx.eventType())) {
            return null;
        }
        long count = ctx.countInWindow(ctx.subjectScope(), WINDOW,
                e -> "security.payment".equals(e.get("topic")) && isFailed(e));
        if (count < THRESHOLD) {
            return null;
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 25,
                count + " failed payments for '" + ctx.subjectKey() + "' within "
                        + WINDOW.toMinutes() + " minutes",
                "Suspend the payment method and notify the fraud team");
    }

    static boolean isFailed(java.util.Map<String, Object> data) {
        if (!"security.payment".equals(data.get("topic"))) {
            return false;
        }
        if ("PAYMENT_FAILED".equals(data.get("eventType"))) {
            return true;
        }
        Object status = data.get("status") != null ? data.get("status") : data.get("outcome");
        return status != null && FAILED_STATUSES.contains(String.valueOf(status).toUpperCase());
    }
}
