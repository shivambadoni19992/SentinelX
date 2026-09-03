package com.sentinelx.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import com.sentinelx.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Deterministic synthetic decisioning — the stand-in for the real risk engine.
 *
 * <p>Rules (evaluated in order, first match wins):
 * <ol>
 *   <li>{@code amount >= declineThreshold} → DECLINED (limit exceeded)</li>
 *   <li>{@code amount >= holdThreshold}    → HELD (manual review)</li>
 *   <li>deterministic risk bucket from the stable hash of
 *       (customerId, deviceId, amount) below {@code declineRiskPercent} → DECLINED</li>
 *   <li>risk bucket below {@code holdRiskPercent} → HELD</li>
 *   <li>otherwise → APPROVED</li>
 * </ol>
 *
 * <p>Being a pure function of the request, it is trivially unit-testable and
 * produces reproducible outcomes for a given payment (useful when replaying a
 * scenario in the Simulation Center).
 */
@Component
public class SyntheticPaymentProcessor {

    /** Outcome of the synthetic decision. */
    public record Decision(PaymentStatus status, String reason) {
    }

    private final PaymentProperties props;

    public SyntheticPaymentProcessor(PaymentProperties props) {
        this.props = props;
    }

    public Decision decide(UUID customerId, String deviceId, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.compareTo(props.declineThreshold()) >= 0) {
            return new Decision(PaymentStatus.DECLINED, "AMOUNT_LIMIT_EXCEEDED");
        }
        if (amount.compareTo(props.holdThreshold()) >= 0) {
            return new Decision(PaymentStatus.HELD, "HIGH_VALUE_REVIEW");
        }

        int bucket = riskBucket(customerId, deviceId, amount);
        if (bucket < props.declineRiskPercent()) {
            return new Decision(PaymentStatus.DECLINED, "RISK_RULE_DECLINE_" + bucket);
        }
        if (bucket < props.holdRiskPercent()) {
            return new Decision(PaymentStatus.HELD, "RISK_RULE_REVIEW_" + bucket);
        }
        return new Decision(PaymentStatus.APPROVED, "SYNTHETIC_APPROVAL_" + bucket);
    }

    /**
     * Stable 0..99 bucket derived from the payment's identity. Uses a 64-bit
     * FNV-1a hash so results are reproducible across JVM runs.
     */
    static int riskBucket(UUID customerId, String deviceId, BigDecimal amount) {
        String seed = (customerId == null ? "no-customer" : customerId.toString())
                + '|' + (deviceId == null ? "no-device" : deviceId)
                + '|' + amount.toPlainString();
        long hash = 0xcbf29ce484222325L;
        for (byte b : seed.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return (int) (Math.abs(hash) % 100);
    }
}