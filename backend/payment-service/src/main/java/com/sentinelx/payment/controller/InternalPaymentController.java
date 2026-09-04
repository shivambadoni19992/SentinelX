package com.sentinelx.payment.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;
import com.sentinelx.payment.repository.PaymentRepository;

/**
 * Internal (service-to-service) payment endpoints used by the alert service's
 * response actions. Not exposed through the gateway; the edge-trust filter
 * only guards {@code /api/**}, so these stay open inside the mesh.
 */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentRepository payments;

    public InternalPaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    /**
     * Forces a payment lifecycle status (e.g. {@code HELD}) from a response
     * action. Returns 404 for unknown ids and 400 for unknown statuses.
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Payment payment = payments.findById(id).orElse(null);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(
                    body.getOrDefault("status", "").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status must be one of PENDING, APPROVED, HELD, DECLINED"));
        }
        payment.setStatus(status);
        payment.setDecisionReason(body.getOrDefault("reason", "forced by alert action"));
        payments.save(payment);
        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getId().toString(),
                "status", payment.getStatus().name(),
                "reason", payment.getDecisionReason() == null ? "" : payment.getDecisionReason()));
    }
}