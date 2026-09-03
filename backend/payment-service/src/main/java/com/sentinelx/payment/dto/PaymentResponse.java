package com.sentinelx.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;
import com.sentinelx.payment.service.DataMasker;

/**
 * API contract for a payment. Matches the SentinelX payment-processing
 * resource exactly; {@code deviceId} and {@code ipAddress} are masked on
 * egress and internal decision reasons are never exposed.
 */
public record PaymentResponse(
        UUID paymentId,
        UUID customerId,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        String deviceId,
        String ipAddress,
        PaymentStatus status,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getCustomerId(),
                p.getMerchantId(),
                p.getAmount(),
                p.getCurrency(),
                DataMasker.maskDeviceId(p.getDeviceId()),
                DataMasker.maskIp(p.getIpAddress()),
                p.getStatus(),
                p.getCreatedAt());
    }
}