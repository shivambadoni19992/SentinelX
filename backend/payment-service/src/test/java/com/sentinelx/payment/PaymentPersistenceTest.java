package com.sentinelx.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.repository.PaymentRepository;

@SpringBootTest
@Testcontainers
class PaymentPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    PaymentRepository payments;

    @Test
    void paymentRoundTrip() {
        Payment p = new Payment();
        p.setUserId(UUID.randomUUID());
        p.setAmount(new BigDecimal("129.9900"));
        p.setCurrency("USD");
        p.setPaymentMethod("card");
        p.setTransactionId("txn-1");
        p.setStatus("SETTLED");
        p = payments.saveAndFlush(p);

        assertThat(p.getId()).isNotNull();
        assertThat(payments.findById(p.getId())).isPresent();
        assertThat(payments.findByStatus("SETTLED")).hasSize(1);
    }
}