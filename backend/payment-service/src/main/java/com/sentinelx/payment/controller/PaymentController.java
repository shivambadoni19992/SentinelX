package com.sentinelx.payment.controller;

import java.util.List;
import java.util.UUID;

import com.sentinelx.payment.dto.CreatePaymentRequest;
import com.sentinelx.payment.dto.PaymentResponse;
import com.sentinelx.payment.entity.PaymentStatus;
import com.sentinelx.payment.security.AuthenticatedRequestFilter;
import com.sentinelx.payment.security.RequestContext;
import com.sentinelx.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Synthetic payment processing API.
 *
 * <pre>
 *   POST /api/payments       create (idempotent when a key is supplied)
 *   GET  /api/payments       list   (ownership-scoped, optional filters)
 *   GET  /api/payments/{id}  fetch  (masked)
 * </pre>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String headerKey,
            HttpServletRequest http) {

        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        String key = normalizeKey(headerKey != null ? headerKey : request.idempotencyKey());

        PaymentService.CreateResult result = payments.create(request, key, caller);
        PaymentResponse body = PaymentResponse.from(result.payment());

        return ResponseEntity
                .status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAY_HEADER, Boolean.toString(result.replay()))
                .body(body);
    }

    @GetMapping
    public List<PaymentResponse> list(
            @RequestParam(name = "customerId", required = false) String customerId,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest http) {

        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        UUID customerFilter = AuthenticatedRequestFilter.uuidOrNull(customerId);
        PaymentStatus statusFilter = parseStatus(status);

        return payments.list(caller, customerFilter, statusFilter).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> byId(@PathVariable String id, HttpServletRequest http) {
        UUID paymentId = AuthenticatedRequestFilter.uuidOrNull(id);
        if (paymentId == null) {
            return ResponseEntity.notFound().build();
        }
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        return ResponseEntity.ok(PaymentResponse.from(payments.get(paymentId, caller)));
    }

    /** Location URI helper (kept package-visible for tests). */
    static String locationFor(UUID paymentId) {
        return UriComponentsBuilder.fromPath("/api/payments/{id}").build(paymentId);
    }

    private static String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static PaymentStatus parseStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be one of PENDING, APPROVED, HELD, DECLINED");
        }
    }
}