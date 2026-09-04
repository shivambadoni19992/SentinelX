package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a payment's amount is far above the customer's recent rolling
 * average (or an absolute ceiling), signalling account takeover or fraud.
 */
@Component
public class UnusualTransactionAmountRule implements DetectionRule {

    public static final String RULE_ID = "UNUSUAL_TRANSACTION_AMOUNT";
    static final Duration HISTORY = Duration.ofHours(1);
    static final double MULTIPLIER = 5.0;
    static final double ABSOLUTE_CEILING = 10_000.0;
    static final int MIN_BASELINE = 3;

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
        double amount = ctx.number("amount", "totalAmount");
        if (amount <= 0) {
            return null;
        }
        var history = ctx.window(ctx.subjectScope(), HISTORY).stream()
                .filter(e -> e.at().isBefore(ctx.occurredAt()))
                .filter(e -> "security.payment".equals(e.data().get("topic"))
                        && "PAYMENT_CREATED".equals(e.data().get("eventType")))
                .toList();
        double avg = history.stream()
                .mapToDouble(e -> amountOf(e.data()))
                .filter(a -> a > 0)
                .average().orElse(0);
        boolean aboveCeiling = amount >= ABSOLUTE_CEILING;
        boolean aboveBaseline = history.size() >= MIN_BASELINE && avg > 0 && amount >= MULTIPLIER * avg;
        if (!aboveCeiling && !aboveBaseline) {
            return null;
        }
        String reason = aboveBaseline
                ? String.format("amount %.2f is %.1fx the recent average %.2f across %d payments for '%s'",
                        amount, amount / avg, avg, history.size(), ctx.subjectKey())
                : String.format("amount %.2f exceeds the absolute ceiling %.2f for '%s'",
                        amount, ABSOLUTE_CEILING, ctx.subjectKey());
        return new DetectionResult(RULE_ID, Severity.HIGH, 30, reason,
                "Hold the transaction for manual fraud review");
    }

    static double amountOf(java.util.Map<String, Object> data) {
        Object v = data.get("amount");
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        Object v2 = data.get("totalAmount");
        if (v2 instanceof Number n2) {
            return n2.doubleValue();
        }
        return 0d;
    }
}
