package com.sentinelx.risk.kafka;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.risk.engine.RiskScoringService;

/**
 * Publishes computed risk decisions as {@code RISK_DECIDED} events to
 * {@code security.alert} so downstream services (alerting, SOC console)
 * can react to scored risk. Message key is the subject, keeping a subject's
 * decisions ordered within a partition.
 */
@Component
public class RiskEventPublisher {

    public static final String TOPIC = "security.alert";
    public static final String EVENT_TYPE = "RISK_DECIDED";

    private static final Logger log = LoggerFactory.getLogger(RiskEventPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public RiskEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Publishes one risk decision; never throws. */
    public void publishRiskEvent(String subject, String sourceTopic, String eventId,
                                 RiskScoringService.ScoredDecision scored) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", EVENT_TYPE);
            payload.put("subject", subject);
            payload.put("sourceTopic", sourceTopic);
            payload.put("eventId", eventId);
            payload.put("score", scored.score());
            payload.put("level", scored.level().name());
            payload.put("reasons", scored.reasons());
            payload.put("action", scored.action());
            payload.put("decidedAt", Instant.now().toString());

            kafkaTemplate.send(TOPIC, subject, MAPPER.writeValueAsString(payload))
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.warn("async risk publish failed for subject {} — {}", subject, ex.getMessage());
                        }
                    });
            log.info("risk event published subject={} level={} score={} eventId={}",
                    subject, scored.level(), scored.score(), eventId);
        } catch (Exception e) {
            log.warn("failed to publish risk event subject={} — {}", subject, e.getMessage());
        }
    }
}