package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires when a payment is made from a device the customer has never used before.
 * Tracks known devices per customer in a sliding window; first-seen devices
 * within the window trigger a NEW_DEVICE detection on the payment topic.
 */
@Component
public class NewDevicePaymentRule implements DetectionRule {

    public static final String RULE_ID = "NEW_DEVICE";
    private static final String SCOPE_PREFIX = "payment-devices:";
    private static final Duration WINDOW = Duration.ofHours(24);

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
        String deviceId = ctx.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        String scope = SCOPE_PREFIX + ctx.subjectKey();
        if (ctx.windows().seenBefore(scope, deviceId)) {
            return null;
        }
        ctx.windows().remember(scope, deviceId);

        double amount = ctx.number("amount", "totalAmount");
        return new DetectionResult(RULE_ID, Severity.HIGH, 25,
                String.format("Payment of %.2f from new device '%s' for customer '%s' — device not seen in last %d hours",
                        amount, deviceId, ctx.subjectKey(), WINDOW.toHours()),
                "Hold payment for device verification and require step-up authentication");
    }
}
