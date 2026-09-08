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
import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;
import com.sentinelx.alert.service.AlertWorkflowService;

/**
 * Consumes {@code RISK_DECIDED} events from {@code security.alert} (published
 * by the risk engine) and opens a {@link SecurityAlert} for each one. The
 * alert is bound to the acting subject (entityType {@code USER} for auth
 * events, entityId a stable id derived from the subject) and an automated
 * response is applied by risk level: RATE_LIMIT for MEDIUM, BLOCK_ACCOUNT for
 * HIGH/CRITICAL. Auto-apply is gated by
 * {@code sentinelx.response.auto-apply-risk-actions} (on by default).
 */
@Component
public class RiskDecisionConsumer {

    public static final String TOPIC = "security.alert";

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SecurityAlertRepository alerts;
    private final AlertWorkflowService workflow;
    private final boolean autoApply;

    public RiskDecisionConsumer(SecurityAlertRepository alerts, AlertWorkflowService workflow,
                                @org.springframework.beans.factory.annotation.Value(
                                        "${sentinelx.response.auto-apply-risk-actions:true}") boolean autoApply) {
        this.alerts = alerts;
        this.workflow = workflow;
        this.autoApply = autoApply;
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
        String level = node.path("level").asText("LOW");
        String subject = node.path("subject").asText("unknown");
        SecurityAlert alert = new SecurityAlert();
        alert.setTitle("Risk " + level + " — " + subject);
        alert.setDescription(joinReasons(node));
        alert.setSeverity(level);
        // Bind the alert to the acting user: the subject (username) is stored on
        // assignedTo so RATE_LIMIT keys on it, and entityId is a stable id derived
        // from the subject so BLOCK_ACCOUNT has a target.
        alert.setEntityType("USER");
        alert.setEntityId(uuid(subject));
        alert.setAssignedTo(subject);
        alert.setEventId(uuid(node.path("eventId").asText(subject)));
        alert.setStatus("OPEN");
        SecurityAlert saved = alerts.save(alert);
        log.info("alert opened from risk decision subject={} level={} eventId={}",
                subject, level, node.path("eventId").asText(""));

        // Automated response policy: MEDIUM → RATE_LIMIT, HIGH → BLOCK_ACCOUNT,
        // CRITICAL → BLOCK_ACCOUNT + HOLD_TRANSACTION (account takeover: block the
        // compromised account AND freeze the suspicious payment).
        if (autoApply && saved.getId() != null) {
            for (AlertAction action : autoActionsFor(level)) {
                try {
                    workflow.applyAction(saved.getId(), action, "auto:risk-" + level);
                } catch (Exception e) {
                    log.warn("auto-apply {} failed for alert {} — {}", action, saved.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Automated response policy. MEDIUM → RATE_LIMIT, HIGH → BLOCK_ACCOUNT,
     * CRITICAL → BLOCK_ACCOUNT + HOLD_TRANSACTION (account-takeover response).
     */
    static java.util.List<AlertAction> autoActionsFor(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "MEDIUM" -> List.of(AlertAction.RATE_LIMIT);
            case "HIGH" -> List.of(AlertAction.BLOCK_ACCOUNT);
            case "CRITICAL" -> List.of(AlertAction.BLOCK_ACCOUNT, AlertAction.HOLD_TRANSACTION);
            default -> List.of();
        };
    }

    /** @deprecated use {@link #autoActionsFor(String)} which returns the full action set. */
    static AlertAction autoActionFor(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "MEDIUM" -> AlertAction.RATE_LIMIT;
            case "HIGH", "CRITICAL" -> AlertAction.BLOCK_ACCOUNT;
            default -> null;
        };
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