package com.sentinelx.payment.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.sentinelx.payment.dto.CreatePaymentRequest;
import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;
import com.sentinelx.payment.repository.PaymentRepository;
import com.sentinelx.payment.security.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment processing application service.
 *
 * <p>Request validation is handled by bean validation at the edge; this class
 * owns persistence, idempotency, authorization scoping, synthetic decisioning
 * and event publication.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Result of a create call — distinguishes a fresh payment from an idempotent replay. */
    public record CreateResult(Payment payment, boolean replay) {
    }

    private final PaymentRepository payments;
    private final SyntheticPaymentProcessor processor;
    private final PaymentEventPublisher events;

    public PaymentService(PaymentRepository payments,
                          SyntheticPaymentProcessor processor,
                          PaymentEventPublisher events) {
        this.payments = payments;
        this.processor = processor;
        this.events = events;
    }

    /**
     * Creates a payment. When an idempotency key is supplied and already known,
     * the stored payment is returned with {@code replay=true} — no new row and
     * no new event is produced.
     */
    @Transactional
    public CreateResult create(CreatePaymentRequest request, String idempotencyKey, RequestContext caller) {
        UUID customerId = resolveCustomerId(request.customerId(), caller);

        if (idempotencyKey != null) {
            Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Payment found = existing.get();
                log.info("payment idempotent replay paymentId={} correlationId={}",
                        found.getId(), MDC.get("correlationId"));
                return new CreateResult(found, true);
            }
        }

        SyntheticPaymentProcessor.Decision decision =
                processor.decide(customerId, request.deviceId(), request.amount());

        Payment payment = new Payment();
        payment.setCustomerId(customerId);
        payment.setMerchantId(request.merchantId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency().toUpperCase());
        payment.setDeviceId(request.deviceId());
        payment.setIpAddress(request.ipAddress());
        payment.setStatus(decision.status());
        payment.setDecisionReason(decision.reason());
        payment.setIdempotencyKey(idempotencyKey);

        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Lost a concurrent race for the same idempotency key: return the winner.
            if (idempotencyKey != null) {
                Optional<Payment> winner = payments.findByIdempotencyKey(idempotencyKey);
                if (winner.isPresent()) {
                    log.info("payment idempotent replay after race paymentId={} correlationId={}",
                            winner.get().getId(), MDC.get("correlationId"));
                    return new CreateResult(winner.get(), true);
                }
            }
            throw e;
        }

        log.info("payment created paymentId={} customerId={} merchantId={} amount={} {} status={} reason={} correlationId={}",
                payment.getId(), payment.getCustomerId(), payment.getMerchantId(),
                payment.getAmount(), payment.getCurrency(), payment.getStatus(),
                payment.getDecisionReason(), MDC.get("correlationId"));

        events.paymentCreated(payment);
        return new CreateResult(payment, false);
    }

    /**
     * Lists payments. Unprivileged callers are always scoped to their own
     * customer id regardless of any filter they pass.
     */
    @Transactional(readOnly = true)
    public List<Payment> list(RequestContext caller, UUID customerFilter, PaymentStatus statusFilter) {
        UUID customer = caller.privileged() ? customerFilter : caller.authenticatedSubject();
        return scopedByStatus(customer, statusFilter);
    }

    private List<Payment> scopedByStatus(UUID customer, PaymentStatus status) {
        if (customer != null && status != null) {
            return payments.findByCustomerIdAndStatusOrderByCreatedAtDesc(customer, status);
        }
        if (customer != null) {
            return payments.findByCustomerIdOrderByCreatedAtDesc(customer);
        }
        if (status != null) {
            return payments.findByStatusOrderByCreatedAtDesc(status);
        }
        return payments.findAllByOrderByCreatedAtDesc();
    }

    /** Fetches one payment, enforcing ownership for unprivileged callers. */
    @Transactional(readOnly = true)
    public Payment get(UUID id, RequestContext caller) {
        Payment payment = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!caller.privileged() && !payment.getCustomerId().equals(caller.authenticatedSubject())) {
            log.warn("forbidden payment read paymentId={} subject={} owner={} correlationId={}",
                    id, caller.authenticatedSubject(), payment.getCustomerId(), MDC.get("correlationId"));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your payment");
        }
        return payment;
    }

    /**
     * Customers may only create payments for themselves; privileged roles may
     * act on behalf of another customer (e.g. analyst-initiated simulation).
     */
    private UUID resolveCustomerId(UUID requested, RequestContext caller) {
        Objects.requireNonNull(caller, "caller");
        if (!caller.privileged()) {
            return caller.authenticatedSubject();
        }
        return requested != null ? requested : caller.authenticatedSubject();
    }
}
