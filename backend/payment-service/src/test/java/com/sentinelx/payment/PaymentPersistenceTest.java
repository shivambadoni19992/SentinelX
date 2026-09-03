package com.sentinelx.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;
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
        p.setCustomerId(UUID.randomUUID());
        p.setMerchantId(UUID.randomUUID());
        p.setAmount(new BigDecimal("129.9900"));
        p.setCurrency("USD");
        p.setDeviceId("device-9f3ab21c");
        p.setIpAddress("203.0.113.45");
        p.setStatus(PaymentStatus.APPROVED);
        p.setDecisionReason("SYNTHETIC_APPROVAL_42");
        p = payments.saveAndFlush(p);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(payments.findById(p.getId())).isPresent();
        assertThat(payments.findByStatusOrderByCreatedAtDesc(PaymentStatus.APPROVED)).hasSize(1);
        assertThat(payments.findByCustomerIdOrderByCreatedAtDesc(p.getCustomerId())).hasSize(1);
    }

    @Test
    void idempotencyKeyIsUnique() {
        Payment a = sample("key-shared");
        Payment b = sample("key-shared");
        payments.saveAndFlush(a);
        assertThat(payments.findByIdempotencyKey("key-shared")).isPresent();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> payments.saveAndFlush(b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusDomainIsConstrainedToProcessingStatuses() {
        Payment p = sample(null);
        p.setStatus(PaymentStatus.HELD);
        assertThat(payments.saveAndFlush(p).getStatus()).isEqualTo(PaymentStatus.HELD);
    }

    private Payment sample(String idempotencyKey) {
        Payment p = new Payment();
        p.setCustomerId(UUID.randomUUID());
        p.setMerchantId(UUID.randomUUID());
        p.setAmount(new BigDecimal("42.5000"));
        p.setCurrency("USD");
        p.setDeviceId("device-12345678");
        p.setIpAddress("198.51.100.7");
        p.setStatus(PaymentStatus.PENDING);
        p.setIdempotencyKey(idempotencyKey);
        return p;
    }
}