package com.sentinelx.alert.kafka;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;

/**
 * Consumes {@code RISK_DECIDED} events from {@code security.alert} (published
 * by the risk engine) and opens a {@link SecurityAlert} for each one. Group
 * is {@code sentinelx-alert-service}; poison records are retried then
 * dead-lettered by the shared error handler in {@link AlertKafkaConfig}.
 */
@Component
public class RiskDecisionConsumer {

    public static final String TOPIC = "security.alert";

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecurityAlertRepository alerts;

    public RiskDecisionConsumer(SecurityAlertRepository alerts) {
        this.alerts = alerts;
    }

    @KafkaListener(topics = TOPIC, groupId = AlertKafkaConfig.GROUP_ID,
            containerFactory = "alertListenerContainerFactory")
    public void onRiskDecision(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            handle(record.value());
        } catch (Exception e) {
            log.warn("failed to process risk decision offset={} — {}", record.offset(), e.getMessage());
            throw new IllegalStateException(e);
        } finally {
            ack.acknowledge();
        }
    }

    void handle(String payload) {
        JsonNode node = parse(payload);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("payload is not a JSON object");
        }
        if (!"RISK_DECIDED".equals(node.path("eventType").asText(""))) {
            return;
        }
        SecurityAlert alert = new SecurityAlert();
        String level = node.path("level").asText("LOW");
        String subject = node.path("subject").asText("unknown");
        alert.setTitle("Risk " + level + " — " + subject);
        alert.setDescription(joinReasons(node));
        alert.setSeverity(level);
        alert.setEntityType("RISK_SUBJECT");
        alert.setEntityId(uuid(node.path("eventId").asText(null)));
        alert.setEventId(uuid(node.path("eventId").asText(null)));
        alert.setStatus("OPEN");
        alerts.save(alert);
        log.info("alert opened from risk decision subject={} level={} eventId={}",
                subject, level, node.path("eventId").asText(""));
    }

    private static String joinReasons(JsonNode node) {
        List<String> reasons = new ArrayList<>();
        node.path("reasons").forEach(r -> reasons.add(r.asText()));
        String joined = String.join("; ", reasons);
        String action = node.path("action").asText("");
        String score = node.path("score").asText("");
        String prefix = "score " + score + " → " + action;
        return joined.isBlank() ? prefix : prefix + " — " + joined;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}