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
 * card-testing and burst-fraud behaviour. Severity escalates with how far
 * the velocity exceeds the threshold:
 *
 * <ul>
 *   <li>5–9 payments in 60s — HIGH (riskContribution 40)</li>
 *   <li>10+ payments in 60s — CRITICAL (riskContribution 60)</li>
 * </ul>
 *
 * <p>The recommended action is always HOLD_TRANSACTION so the platform's
 * auto-response freezes the payment for fraud review. Combined with the
 * TRANSACTION_VELOCITY risk signal (weight 35, max stack 2), a sustained
 * burst from one subject reaches the HIGH risk band on its own.
 */
@Component
public class TransactionVelocityRule implements DetectionRule {

    public static final String RULE_ID = "TRANSACTION_VELOCITY";
    static final Duration WINDOW = Duration.ofSeconds(60);
    static final int THRESHOLD = 5;
    static final int CRITICAL_THRESHOLD = 10;

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
        if (count >= CRITICAL_THRESHOLD) {
            return new DetectionResult(RULE_ID, Severity.CRITICAL, 60,
                    count + " payments initiated by '" + ctx.subjectKey() + "' within "
                            + WINDOW.toSeconds() + " seconds — extreme burst velocity",
                    "HOLD_TRANSACTION — freeze payment and flag for immediate fraud review");
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 40,
                count + " payments initiated by '" + ctx.subjectKey() + "' within "
                        + WINDOW.toSeconds() + " seconds",
                "HOLD_TRANSACTION — apply velocity limits and hold payment for review");
    }
}
