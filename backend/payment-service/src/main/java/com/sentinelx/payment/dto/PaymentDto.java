package com.sentinelx.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.payment.entity.Payment;

/** API contract for a Payment — entities are never exposed over HTTP. */
public record PaymentDto(
        UUID id,
        UUID userId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String transactionId,
        String status,
        BigDecimal riskScore,
        String failureReason,
        Instant originatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentDto from(Payment p) {
        return new PaymentDto(p.getId(), p.getUserId(), p.getOrderId(), p.getAmount(),
                p.getCurrency(), p.getPaymentMethod(), p.getTransactionId(), p.getStatus(),
                p.getRiskScore(), p.getFailureReason(), p.getOriginatedAt(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public Payment toEntity() {
        Payment p = new Payment();
        p.setUserId(userId);
        p.setOrderId(orderId);
        p.setAmount(amount);
        p.setCurrency(currency == null ? "USD" : currency);
        p.setPaymentMethod(paymentMethod);
        p.setTransactionId(transactionId);
        p.setStatus(status == null ? "PENDING" : status);
        p.setRiskScore(riskScore);
        p.setFailureReason(failureReason);
        p.setOriginatedAt(originatedAt);
        return p;
    }
}