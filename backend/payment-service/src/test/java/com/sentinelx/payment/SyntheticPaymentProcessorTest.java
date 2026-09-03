package com.sentinelx.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.sentinelx.payment.entity.PaymentStatus;
import com.sentinelx.payment.service.PaymentProperties;
import com.sentinelx.payment.service.SyntheticPaymentProcessor;

/**
 * Table-driven tests for the deterministic synthetic decisioning rules.
 * Pure unit test — no Spring context.
 */
class SyntheticPaymentProcessorTest {

    private final PaymentProperties props = new PaymentProperties(
            new BigDecimal("10000.00"), new BigDecimal("100000.00"), 10, 3, "sentinelx.payments.created");
    private final SyntheticPaymentProcessor processor = new SyntheticPaymentProcessor(props);

    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String DEVICE = "device-9f3ab21c";

    @Test
    void amountAtDeclineThresholdIsDeclined() {
        var d = processor.decide(CUSTOMER, DEVICE, new BigDecimal("100000.00"));
        assertThat(d.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(d.reason()).isEqualTo("AMOUNT_LIMIT_EXCEEDED");
    }

    @Test
    void amountAboveDeclineThresholdIsDeclined() {
        var d = processor.decide(CUSTOMER, DEVICE, new BigDecimal("250000.00"));
        assertThat(d.status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void amountAtHoldThresholdIsHeld() {
        var d = processor.decide(CUSTOMER, DEVICE, new BigDecimal("10000.00"));
        assertThat(d.status()).isEqualTo(PaymentStatus.HELD);
        assertThat(d.reason()).isEqualTo("HIGH_VALUE_REVIEW");
    }

    @Test
    void smallPaymentIsDeterministicAndInDomain() {
        for (int i = 0; i < 50; i++) {
            UUID customer = UUID.randomUUID();
            var d = processor.decide(customer, "device-" + i, new BigDecimal("25.00"));
            assertThat(d.status()).isIn(PaymentStatus.APPROVED, PaymentStatus.HELD, PaymentStatus.DECLINED);
            // Same inputs must always produce the same decision.
            assertThat(processor.decide(customer, "device-" + i, new BigDecimal("25.00")))
                    .isEqualTo(d);
        }
    }

    @Test
    void riskBucketsRespectConfiguredPercentages() {
        int declined = 0;
        int held = 0;
        int approved = 0;
        for (int i = 0; i < 500; i++) {
            var d = processor.decide(UUID.randomUUID(), "device-" + i, new BigDecimal("40.00"));
            switch (d.status()) {
                case DECLINED -> declined++;
                case HELD -> held++;
                case APPROVED -> approved++;
                default -> { /* PENDING is not produced by the processor */ }
            }
        }
        // decline < 3%, hold < 10% (buckets are cumulative), remainder approved.
        assertThat(declined).isLessThanOrEqualTo(500 * 3 / 100 + 10);
        assertThat(held).isLessThanOrEqualTo(500 * 10 / 100 + 10);
        assertThat(approved).isGreaterThan(500 * 80 / 100 - 10);
    }

    @Test
    void nullDeviceStillYieldsStableDecision() {
        var d1 = processor.decide(CUSTOMER, null, new BigDecimal("12.34"));
        var d2 = processor.decide(CUSTOMER, null, new BigDecimal("12.34"));
        assertThat(d1).isEqualTo(d2);
    }
}