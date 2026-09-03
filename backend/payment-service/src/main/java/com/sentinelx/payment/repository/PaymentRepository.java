package com.sentinelx.payment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllByOrderByCreatedAtDesc();

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<Payment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Payment> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, PaymentStatus status);
}