package com.sentinelx.alert.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.entity.SecurityAlert;

/**
 * Applies a response {@link AlertAction} to real application state:
 *
 * <ul>
 *   <li>{@code HOLD_TRANSACTION}     → payment status becomes HELD (payment-service)</li>
 *   <li>{@code BLOCK_ACCOUNT}        → user account_status becomes BLOCKED (auth-service)</li>
 *   <li>{@code RATE_LIMIT}           → Redis rate-limit lock is applied for the subject</li>
 *   <li>{@code REQUIRE_VERIFICATION} → Redis step-up verification flag is set</li>
 *   <li>{@code MONITOR}              → Redis monitoring flag is set</li>
 *   <li>{@code ALLOW}                → explicit allow, no downstream change</li>
 * </ul>
 *
 * <p>Every applied action returns a structured detail map that the caller
 * stores on the alert; the alert service emits one audit event per action.
 */
@Service
public class ResponseActionExecutor {

    public static final String RATE_LIMIT_KEY_PREFIX = "sentinelx:ratelimit:block:";
    public static final String VERIFICATION_KEY_PREFIX = "sentinelx:verification:required:";
    public static final String MONITOR_KEY_PREFIX = "sentinelx:monitor:";

    private static final Logger log = LoggerFactory.getLogger(ResponseActionExecutor.class);

    private final RestClient paymentClient;
    private final RestClient authClient;
    private final StringRedisTemplate redis;
    private final Duration rateLimitTtl;
    private final Duration verificationTtl;

    public ResponseActionExecutor(
            RestClient.Builder paymentClientBuilder,
            RestClient.Builder authClientBuilder,
            @Value("${sentinelx.response.payment-service-url}") String paymentServiceUrl,
            @Value("${sentinelx.response.auth-service-url}") String authServiceUrl,
            @Value("${sentinelx.response.rate-limit-ttl}") Duration rateLimitTtl,
            @Value("${sentinelx.response.verification-ttl}") Duration verificationTtl,
            StringRedisTemplate redis) {
        this.paymentClient = paymentClientBuilder.baseUrl(paymentServiceUrl).build();
        this.authClient = authClientBuilder.baseUrl(authServiceUrl).build();
        this.redis = redis;
        this.rateLimitTtl = rateLimitTtl;
        this.verificationTtl = verificationTtl;
    }

    /**
     * Applies the action and returns the structured detail stored on the
     * alert. Downstream failures are captured in the detail, not thrown.
     */
    public Map<String, Object> apply(SecurityAlert alert, AlertAction action) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action.name());
        detail.put("appliedAt", Instant.now().toString());
        String subject = subjectOf(alert);
        detail.put("subject", subject);

        switch (action) {
            case HOLD_TRANSACTION -> detail.putAll(holdTransaction(alert));
            case BLOCK_ACCOUNT -> detail.putAll(blockAccount(alert));
            case RATE_LIMIT -> detail.putAll(applyRateLimit(subject));
            case REQUIRE_VERIFICATION -> detail.putAll(requireVerification(subject));
            case MONITOR -> detail.putAll(monitor(subject));
            case ALLOW -> detail.put("state", "ALLOWED — no downstream change");
        }
        log.info("response action applied alert={} action={} subject={} detail={}",
                alert.getId(), action, subject, detail);
        return detail;
    }

    /** HOLD_TRANSACTION → payment status becomes HELD in payment-service. */
    private Map<String, Object> holdTransaction(SecurityAlert alert) {
        Map<String, Object> out = new LinkedHashMap<>();
        UUID paymentId = alert.getEntityId();
        if (!"PAYMENT".equalsIgnoreCase(alert.getEntityType()) || paymentId == null) {
            out.put("state", "SKIPPED — no payment bound to this alert");
            return out;
        }
        out.put("target", "payment:" + paymentId);
        try {
            var response = paymentClient.post()
                    .uri("/internal/payments/{id}/status", paymentId)
                    .body(Map.of("status", "HELD", "reason", "alert " + alert.getId()))
                    .retrieve()
                    .toBodilessEntity();
            out.put("state", "APPLIED");
            out.put("paymentStatus", "HELD");
            out.put("httpStatus", response.getStatusCode().value());
        } catch (Exception e) {
            out.put("state", "FAILED");
            out.put("error", e.getMessage());
        }
        return out;
    }

    /** BLOCK_ACCOUNT → user account_status becomes BLOCKED in auth-service. */
    private Map<String, Object> blockAccount(SecurityAlert alert) {
        Map<String, Object> out = new LinkedHashMap<>();
        UUID userId = alert.getEntityId();
        if (!"USER".equalsIgnoreCase(alert.getEntityType()) || userId == null) {
            out.put("state", "SKIPPED — no user bound to this alert");
            return out;
        }
        out.put("target", "user:" + userId);
        try {
            var response = authClient.post()
                    .uri("/internal/users/{id}/status", userId)
                    .body(Map.of("status", "BLOCKED", "reason", "alert " + alert.getId()))
                    .retrieve()
                    .toBodilessEntity();
            out.put("state", "APPLIED");
            out.put("accountStatus", "BLOCKED");
            out.put("httpStatus", response.getStatusCode().value());
        } catch (Exception e) {
            out.put("state", "FAILED");
            out.put("error", e.getMessage());
        }
        return out;
    }

    /** RATE_LIMIT → Redis blocks the subject for the rate-limit TTL. */
    private Map<String, Object> applyRateLimit(String subject) {
        String key = RATE_LIMIT_KEY_PREFIX + subject;
        redis.opsForValue().set(key, "1", rateLimitTtl);
        return Map.of("state", "APPLIED", "redisKey", key, "ttl", rateLimitTtl.toString());
    }

    /** REQUIRE_VERIFICATION → Redis forces step-up auth for the subject. */
    private Map<String, Object> requireVerification(String subject) {
        String key = VERIFICATION_KEY_PREFIX + subject;
        redis.opsForValue().set(key, "1", verificationTtl);
        return Map.of("state", "APPLIED", "redisKey", key, "ttl", verificationTtl.toString());
    }

    /** MONITOR → Redis flags the subject for elevated review. */
    private Map<String, Object> monitor(String subject) {
        String key = MONITOR_KEY_PREFIX + subject;
        redis.opsForValue().set(key, "1", verificationTtl);
        return Map.of("state", "APPLIED", "redisKey", key, "ttl", verificationTtl.toString());
    }

    private static String subjectOf(SecurityAlert alert) {
        if (alert.getEntityId() != null) {
            return alert.getEntityId().toString();
        }
        if (alert.getAssignedTo() != null && !alert.getAssignedTo().isBlank()) {
            return alert.getAssignedTo();
        }
        return "alert:" + alert.getId();
    }
}