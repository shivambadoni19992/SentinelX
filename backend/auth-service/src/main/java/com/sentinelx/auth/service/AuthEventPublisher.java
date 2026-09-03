package com.sentinelx.auth.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.sentinelx.auth.entity.User;

/**
 * Publishes authentication events ({@code LOGIN_SUCCESS}, {@code LOGIN_FAILED},
 * {@code LOGIN_BLOCKED}) as JSON to the {@code security.auth} topic.
 *
 * <p>Publishing is best-effort by design: a Kafka outage must never fail an
 * authentication request. Each event carries a correlation id — taken from the
 * MDC when set (e.g. propagated by the API gateway), otherwise generated —
 * and is sent with the user id as the key so a subject's auth events stay
 * ordered within a partition.
 */
@Component
public class AuthEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthEventPublisher.class);

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_BLOCKED = "LOGIN_BLOCKED";

    public static final String TOPIC = "security.auth";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AuthEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Serializes and publishes the auth event; never throws. */
    public void publish(String eventType, User user, String username, String ipAddress, String reason) {
        try {
            String correlationId = currentOrNewCorrelationId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("userId", user == null ? null : user.getId().toString());
            payload.put("username", username);
            payload.put("actor", username);
            payload.put("action", eventType);
            payload.put("outcome", eventType.equals(LOGIN_SUCCESS) ? "SUCCESS" : "FAILURE");
            payload.put("severity", eventType.equals(LOGIN_SUCCESS) ? "LOW" : "MEDIUM");
            payload.put("sourceIp", ipAddress);
            payload.put("occurredAt", java.time.Instant.now().toString());
            payload.put("reason", reason);
            payload.put("correlationId", correlationId);

            String key = user == null ? (username == null ? "unknown" : username) : user.getId().toString();
            kafkaTemplate.send(TOPIC, key, Json.write(payload))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("async send failed for {} username={} — {}", eventType, username, ex.getMessage());
                        }
                    });
            // Same id travels in the Kafka header for header-based propagation.
            MDC.put("correlationId", correlationId);
            log.info("event published type={} topic={} key={} correlationId={}", eventType, TOPIC, key, correlationId);
        } catch (Exception e) {
            log.warn("failed to publish {} for username={} — {}", eventType, username, e.getMessage());
        }
    }

    private static String currentOrNewCorrelationId() {
        String existing = MDC.get("correlationId");
        return existing == null || existing.isBlank() ? UUID.randomUUID().toString() : existing;
    }

    /** Minimal JSON writer to keep the event payload dependency-free and ordered. */
    static final class Json {
        private Json() {
        }

        static String write(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
            }
            return sb.append('}').toString();
        }

        private static String value(Object v) {
            if (v == null) {
                return "null";
            }
            if (v instanceof Number || v instanceof Boolean) {
                return v.toString();
            }
            return quote(v.toString());
        }

        private static String quote(String s) {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}
