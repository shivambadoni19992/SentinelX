package com.sentinelx.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * SentinelX payment-service — synthetic payment processing.
 *
 * <p>Endpoints: {@code POST /api/payments}, {@code GET /api/payments},
 * {@code GET /api/payments/{id}}. Decisions are synthetic and deterministic;
 * {@code PAYMENT_CREATED} events are published to Kafka after persistence.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
