package com.sentinelx.alert.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Emits one audit event per alert status change or response action to the
 * platform's {@code security.audit} topic, where security-event-service
 * normalizes them into durable {@code AuditLog} entries.
 */
@Component
public class AuditEventPublisher {

    public static final String TOPIC = "security.audit";

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public AuditEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void alertStatusChanged(java.util.UUID alertId, String oldStatus, String newStatus, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "ALERT_STATUS_CHANGED");
        payload.put("alertId", alertId.toString());
        payload.put("fromStatus", oldStatus);
        payload.put("toStatus", newStatus);
        payload.put("actor", actor);
        payload.put("occurredAt", java.time.Instant.now().toString());
        publish(alertId.toString(), payload);
    }

    public void alertActionApplied(java.util.UUID alertId, String action, String actor,
                                   Map<String, Object> detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "ALERT_ACTION_APPLIED");
        payload.put("alertId", alertId.toString());
        payload.put("action", action);
        payload.put("actor", actor);
        payload.put("detail", detail);
        payload.put("occurredAt", java.time.Instant.now().toString());
        publish(alertId.toString(), payload);
    }

    private void publish(String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(TOPIC, key, MAPPER.writeValueAsString(payload));
            log.info("audit event published type={} alertId={}", payload.get("eventType"), key);
        } catch (Exception e) {
            log.warn("failed to publish audit event alertId={} — {}", key, e.getMessage());
        }
    }
}