package com.sentinelx.detection.rule.payment;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Correlates the full payment-fraud pattern for a subject within a trailing
 * window: high-value transactions + high velocity + new device + suspicious IP.
 * Each indicator is signalled by a distinct payment event characteristic, so
 * the rule reads the subject's recent history (through the shared
 * {@code WindowStore} behind {@link DetectionContext}) rather than owning state.
 *
 * <p>Fires on any payment event for the subject. Severity escalates with how
 * many fraud indicators are present.
 */
@Component
public class PaymentFraudSuspectedRule implements DetectionRule {

    public static final String RULE_ID = "PAYMENT_FRAUD_SUSPECTED";
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final double HIGH_VALUE_THRESHOLD = 5_000.0;
    private static final int VELOCITY_THRESHOLD = 3;

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
        String subjectScope = ctx.subjectScope();
        List<com.sentinelx.detection.model.WindowStore.Entry> events = ctx.window(subjectScope, WINDOW);

        int highValueCount = 0;
        int paymentCount = 0;
        Set<String> devices = new LinkedHashSet<>();
        Set<String> ips = new LinkedHashSet<>();
        boolean hasNewDevice = false;
        boolean hasSuspiciousIp = false;
        double maxAmount = 0;
        double totalAmount = 0;

        for (com.sentinelx.detection.model.WindowStore.Entry e : events) {
            Map<String, Object> d = e.data();
            Object topic = d.get("topic");
            Object eventType = d.get("eventType");
            if (!"security.payment".equals(topic) || eventType == null) {
                continue;
            }
            if (!"PAYMENT_CREATED".equals(eventType) && !"PAYMENT_AUTHORIZED".equals(eventType)) {
                continue;
            }
            paymentCount++;
            Object amount = d.get("amount");
            double amt = amount instanceof Number n ? n.doubleValue() : 0;
            totalAmount += amt;
            if (amt > maxAmount) {
                maxAmount = amt;
            }
            if (amt >= HIGH_VALUE_THRESHOLD) {
                highValueCount++;
            }
            String device = asText(d.get("deviceId"));
            if (device != null && !device.isBlank()) {
                if (!devices.contains(device)) {
                    if (!devices.isEmpty()) {
                        hasNewDevice = true;
                    }
                    devices.add(device);
                }
            }
            String ip = asText(d.get("sourceIp"));
            if (ip != null && !ip.isBlank()) {
                if (!ips.contains(ip)) {
                    if (!ips.isEmpty()) {
                        hasSuspiciousIp = true;
                    }
                    ips.add(ip);
                }
            }
            Object suspiciousIpFlag = d.get("suspiciousIp");
            if (Boolean.TRUE.equals(suspiciousIpFlag) || "true".equals(String.valueOf(suspiciousIpFlag))) {
                hasSuspiciousIp = true;
            }
            Object newDeviceFlag = d.get("newDevice");
            if (Boolean.TRUE.equals(newDeviceFlag) || "true".equals(String.valueOf(newDeviceFlag))) {
                hasNewDevice = true;
            }
        }

        boolean highVelocity = paymentCount >= VELOCITY_THRESHOLD;
        boolean highValue = highValueCount > 0;

        // Need at least 2 fraud indicators to fire
        int indicators = (highValue ? 1 : 0) + (highVelocity ? 1 : 0)
                + (hasNewDevice ? 1 : 0) + (hasSuspiciousIp ? 1 : 0);
        if (indicators < 2) {
            return null;
        }

        if ((highValue && highVelocity) || indicators >= 3) {
            return new DetectionResult(RULE_ID, Severity.CRITICAL, 65,
                    String.format("Payment fraud confirmed for '%s': %d payments (max %.2f) in %d min, "
                            + "%d high-value, newDevice=%s, suspiciousIp=%s",
                            ctx.subjectKey(), paymentCount, (double) Math.round(maxAmount * 100.0) / 100.0,
                            WINDOW.toMinutes(), highValueCount, hasNewDevice, hasSuspiciousIp),
                    "HOLD_TRANSACTION — freeze payment and flag for immediate fraud review");
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 50,
                String.format("Payment fraud suspected for '%s': %d payments (max %.2f) in %d min, "
                        + "%d high-value, newDevice=%s, suspiciousIp=%s",
                        ctx.subjectKey(), paymentCount, (double) Math.round(maxAmount * 100.0) / 100.0,
                        WINDOW.toMinutes(), highValueCount, hasNewDevice, hasSuspiciousIp),
                "HOLD_TRANSACTION — review payment for potential fraud");
    }

    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
