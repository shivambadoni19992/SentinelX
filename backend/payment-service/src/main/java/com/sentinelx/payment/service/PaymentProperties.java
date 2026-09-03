package com.sentinelx.payment.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for the synthetic payment processor. */
@ConfigurationProperties(prefix = "sentinelx.payments")
public record PaymentProperties(
        /** Amount at or above which a payment is held for review. */
        BigDecimal holdThreshold,
        /** Amount at or above which a payment is declined outright. */
        BigDecimal declineThreshold,
        /** Deterministic 0..99 risk bucket under which a payment is held. */
        int holdRiskPercent,
        /** Deterministic 0..99 risk bucket under which a payment is declined. */
        int declineRiskPercent,
        /** Kafka topic for PAYMENT_CREATED events. */
        String eventTopic) {

    public PaymentProperties {
        if (holdThreshold == null) holdThreshold = new BigDecimal("10000.00");
        if (declineThreshold == null) declineThreshold = new BigDecimal("100000.00");
        if (holdRiskPercent < 0) holdRiskPercent = 10;
        if (declineRiskPercent < 0) declineRiskPercent = 3;
        if (eventTopic == null || eventTopic.isBlank()) eventTopic = "sentinelx.payments.created";
    }
}