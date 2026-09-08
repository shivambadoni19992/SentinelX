package com.sentinelx.detection.rule.auth;

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
 * Correlates the full account-takeover chain for a subject within a trailing
 * window: failed logins → login from a new IP → login from a new device →
 * high-value payment. Each hop is signalled by a distinct event kind, so the
 * rule reads the subject's recent history (through the shared {@code WindowStore}
 * behind {@link DetectionContext}) rather than owning state.
 *
 * <p>Fires on any event for the subject (auth or payment). Severity escalates
 * with how much of the chain is present: a new-IP/device login after failures
 * is {@code HIGH}; a high-value payment on top of that is {@code CRITICAL}.
 */
@Component
public class AccountTakeoverSuspectedRule implements DetectionRule {

    public static final String RULE_ID = "ACCOUNT_TAKEOVER_SUSPECTED";
    private static final Duration WINDOW = Duration.ofMinutes(30);
    private static final int FAILED_LOGIN_HOPS = 3;
    private static final double HIGH_VALUE_THRESHOLD = 1_000.0;

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.auth", "security.payment");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        String subjectScope = ctx.subjectScope();
        List<com.sentinelx.detection.model.WindowStore.Entry> events = ctx.window(subjectScope, WINDOW);

        int failedLogins = 0;
        Set<String> failedIps = new LinkedHashSet<>();
        Set<String> failedDevices = new LinkedHashSet<>();
        boolean successFromNewIp = false;
        boolean successFromNewDevice = false;
        boolean successfulLogin = false;
        boolean highValuePayment = false;

        for (com.sentinelx.detection.model.WindowStore.Entry e : events) {
            Map<String, Object> d = e.data();
            Object topic = d.get("topic");
            Object eventType = d.get("eventType");
            if (eventType == null) {
                continue;
            }
            String ip = asText(d.get("sourceIp"));
            String device = asText(d.get("deviceId"));

            if ("security.auth".equals(topic)) {
                if ("LOGIN_FAILED".equals(eventType)) {
                    failedLogins++;
                    if (ip != null) {
                        failedIps.add(ip);
                    }
                    if (device != null) {
                        failedDevices.add(device);
                    }
                } else if ("LOGIN_SUCCESS".equals(eventType)) {
                    successfulLogin = true;
                    boolean newIp = ip != null && !failedIps.isEmpty() && !failedIps.contains(ip);
                    boolean newDevice = device != null && !failedDevices.isEmpty() && !failedDevices.contains(device);
                    if (newIp) {
                        successFromNewIp = true;
                    }
                    if (newDevice) {
                        successFromNewDevice = true;
                    }
                }
            } else if ("security.payment".equals(topic)) {
                if ("PAYMENT_CREATED".equals(eventType) || "PAYMENT_AUTHORIZED".equals(eventType)) {
                    Object amount = d.get("amount");
                    double amt = amount instanceof Number n ? n.doubleValue() : 0;
                    if (amt >= HIGH_VALUE_THRESHOLD) {
                        highValuePayment = true;
                    }
                }
            }
        }

        boolean hasFailures = failedLogins >= FAILED_LOGIN_HOPS;
        boolean takeoverLogin = successFromNewIp && successFromNewDevice;
        if (!hasFailures || !takeoverLogin) {
            return null;
        }

        if (highValuePayment) {
            return new DetectionResult(RULE_ID, Severity.CRITICAL, 60,
                    "Account takeover confirmed for '" + ctx.subjectKey() + "': "
                            + failedLogins + " failed logins followed by successful login from new IP + new device, "
                            + "then high-value payment",
                    "BLOCK_ACCOUNT and HOLD_TRANSACTION — the account is compromised and funds are at risk");
        }
        return new DetectionResult(RULE_ID, Severity.HIGH, 45,
                "Account takeover suspected for '" + ctx.subjectKey() + "': "
                        + failedLogins + " failed logins followed by successful login from new IP + new device",
                "BLOCK_ACCOUNT — force password reset and require step-up authentication");
    }

    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
