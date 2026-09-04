package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a customer initiates payments faster than any human would —
 * card-testing and burst-fraud behaviour.
 */
@Component
public class TransactionVelocityRule implements DetectionRule {

    public static final String RULE_ID = "TRANSACTION_VELOCITY";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int THRESHOLD = 5;

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
        if (!"PAYMENT_CREATED".equals(ctx.eventType())) {
            return null;
        }
        long count = ctx.countInWindow(ctx.subjectScope(), WINDOW,
                e -> "security.payment".equals(e.get("topic"))
                        && "PAYMENT_CREATED".equals(e.get("eventType")));
        if (count < THRESHOLD) {
            return null;
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 30,
                count + " payments initiated by '" + ctx.subjectKey() + "' within "
                        + WINDOW.toSeconds() + " seconds",
                "Apply velocity limits and require step-up verification");
    }
}
