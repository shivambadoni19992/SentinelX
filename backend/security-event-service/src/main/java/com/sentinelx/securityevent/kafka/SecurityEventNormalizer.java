package com.sentinelx.securityevent.kafka;

import java.net.InetAddress;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelx.securityevent.entity.SecurityEvent;

/**
 * The normalizing consumer. Subscribes to every {@code security.*} event topic
 * and maps each JSON event onto the platform-wide {@code SecurityEvent} model
 * (schema {@code events.security_events}), whatever producing service emitted
 * it. The Kafka record's {@code correlationId} header (or payload field, or
 * message key) is promoted into the MDC so every log line — including retries
 * and dead-lettering — carries the originating request's id.
 */
@Component
public class SecurityEventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventNormalizer.class);

    static final java.util.Set<String> RESERVED_FIELDS = java.util.Set.of(
            "eventType", "userId", "customerId", "deviceId", "sessionId", "actor", "username",
            "action", "outcome", "status", "severity", "sourceIp", "ipAddress",
            "occurredAt", "createdAt", "timestamp", "correlationId");

    private final SecurityEventStore store;

    public SecurityEventNormalizer(SecurityEventStore store) {
        this.store = store;
    }

    @KafkaListener(
            topics = {
                    KafkaTopics.AUTH, KafkaTopics.PAYMENT, KafkaTopics.API, KafkaTopics.RETAIL,
                    KafkaTopics.NETWORK, KafkaTopics.RISK, KafkaTopics.ALERT, KafkaTopics.AUDIT
            },
            groupId = KafkaConsumerConfig.GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        try {
            String payloadCorrelationId = Jackson.parse(record.value()) == null ? null
                    : Jackson.parse(record.value()).path("correlationId").asText(null);
            String correlationId = KafkaConsumerConfig.promoteCorrelationId(record, payloadCorrelationId);
            SecurityEvent event = normalize(topic, record.key(), record.value(),
                    correlationId, KafkaConsumerConfig.headersToMap(record));
            store.persist(event);
            log.info("normalized event topic={} partition={} offset={} eventType={} outcome={} severity={} correlationId={}",
                    topic, record.partition(), record.offset(), event.getEventType(),
                    event.getOutcome(), event.getSeverity(), MDC.get(KafkaConsumerConfig.MDC_CORRELATION_ID));
        } catch (Exception e) {
            // Rethrowing lets the DefaultErrorHandler retry, then dead-letter.
            log.warn("failed to normalize record topic={} partition={} offset={} — {}",
                    topic, record.partition(), record.offset(), e.getMessage());
            throw new EventNormalizationException(e);
        } finally {
            ack.acknowledge();
            MDC.remove(KafkaConsumerConfig.MDC_CORRELATION_ID);
        }
    }

    /** Maps a topic-specific JSON payload onto the canonical SecurityEvent model. */
    public SecurityEvent normalize(String topic, String key, String json, String correlationId,
            Map<String, String> headers) {
        JsonNode root = Jackson.parse(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("payload is not a JSON object: " + json);
        }

        SecurityEvent e = new SecurityEvent();
        e.setEventType(root.path("eventType").asText(defaultEventType(topic)));
        e.setUserId(uuid(root, "userId", "customerId"));
        e.setDeviceId(uuid(root, "deviceId"));
        e.setSessionId(uuid(root, "sessionId"));
        e.setActor(root.path("actor").asText(root.path("username").asText(null)));
        e.setAction(root.path("action").asText(e.getEventType()));
        e.setOutcome(normalizeOutcome(root.path("outcome").asText(root.path("status").asText("UNKNOWN"))));
        e.setSeverity(normalizeSeverity(root.path("severity").asText(defaultSeverity(topic))));
        e.setSourceIp(inetAddress(root, "sourceIp", "ipAddress"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceTopic", topic);
        metadata.put("kafkaKey", key);
        if (correlationId != null) {
            metadata.put("correlationId", correlationId);
        }
        if (headers != null && !headers.isEmpty()) {
            metadata.put("headers", new LinkedHashMap<>(headers));
        }
        // Remaining unmapped payload fields are preserved verbatim.
        Map<String, Object> extras = new LinkedHashMap<>();
        var fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!RESERVED_FIELDS.contains(field.getKey())) {
                extras.put(field.getKey(), Jackson.toValue(field.getValue()));
            }
        }
        if (!extras.isEmpty()) {
            metadata.put("payload", extras);
        }
        e.setMetadata(metadata);

        e.setOccurredAt(instant(root, "occurredAt", "createdAt", "timestamp"));
        return e;
    }

    static String defaultEventType(String topic) {
        return switch (topic) {
            case KafkaTopics.AUTH -> "AUTH_EVENT";
            case KafkaTopics.PAYMENT -> "PAYMENT_EVENT";
            case KafkaTopics.API -> "API_EVENT";
            case KafkaTopics.RETAIL -> "RETAIL_EVENT";
            case KafkaTopics.NETWORK -> "NETWORK_EVENT";
            case KafkaTopics.RISK -> "RISK_EVENT";
            case KafkaTopics.ALERT -> "ALERT_EVENT";
            case KafkaTopics.AUDIT -> "AUDIT_EVENT";
            default -> "SECURITY_EVENT";
        };
    }

    static String defaultSeverity(String topic) {
        return switch (topic) {
            case KafkaTopics.ALERT, KafkaTopics.NETWORK, KafkaTopics.RISK -> "HIGH";
            default -> "LOW";
        };
    }

    static String normalizeOutcome(String raw) {
        if (raw == null) {
            return "UNKNOWN";
        }
        return switch (raw.toUpperCase()) {
            case "COMPLETED", "APPROVED", "SUCCESS", "SUCCESSFUL", "ALLOWED", "DELIVERED" -> "SUCCESS";
            case "DECLINED", "FAILED", "FAILURE", "ERROR", "DENIED", "BLOCKED", "REJECTED" -> "FAILURE";
            case "HOLD", "PENDING", "REVIEW", "FLAGGED" -> "PENDING";
            default -> raw.toUpperCase();
        };
    }

    static String normalizeSeverity(String raw) {
        if (raw == null) {
            return "LOW";
        }
        return switch (raw.toUpperCase()) {
            case "CRITICAL", "FATAL" -> "CRITICAL";
            case "HIGH", "WARN", "WARNING" -> "HIGH";
            case "MEDIUM", "MODERATE" -> "MEDIUM";
            case "INFO", "INFORMATIONAL", "DEBUG" -> "LOW";
            default -> raw.toUpperCase();
        };
    }

    private static UUID uuid(JsonNode root, String... fields) {
        for (String f : fields) {
            String v = root.path(f).asText(null);
            if (v != null && !v.isBlank()) {
                try {
                    return UUID.fromString(v);
                } catch (IllegalArgumentException ignored) {
                    // fall through to the next candidate field
                }
            }
        }
        return null;
    }

    private static InetAddress inetAddress(JsonNode root, String... fields) {
        for (String f : fields) {
            String v = root.path(f).asText(null);
            if (v != null && !v.isBlank()) {
                try {
                    return InetAddress.getByName(v);
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private static Instant instant(JsonNode root, String... fields) {
        for (String f : fields) {
            String v = root.path(f).asText(null);
            if (v != null && !v.isBlank()) {
                try {
                    return Instant.parse(v);
                } catch (DateTimeParseException ignored) {
                    // fall through
                }
            }
        }
        return Instant.now();
    }

    /** Wraps any normalization failure so the error handler can retry. */
    public static class EventNormalizationException extends RuntimeException {
        public EventNormalizationException(Throwable cause) {
            super(cause);
        }
    }

    /** Minimal JSON access helper. */
    static final class Jackson {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private Jackson() {
        }

        static JsonNode parse(String json) {
            try {
                return MAPPER.readTree(json);
            } catch (Exception e) {
                return null;
            }
        }

        static Object toValue(JsonNode node) {
            try {
                return MAPPER.convertValue(node, Object.class);
            } catch (IllegalArgumentException e) {
                return node.toString();
            }
        }
    }
}
