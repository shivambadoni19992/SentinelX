package com.sentinelx.risk.kafka;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.risk.engine.RiskScoringService;
import com.sentinelx.risk.model.RiskSignal;

/**
 * Consumes detection events published by the detection engine on
 * {@code security.risk} ({@code eventType=DETECTION_RAISED}) and feeds them
 * into the {@link RiskScoringService} as risk signals. Every consumed record
 * is acknowledged after the signal is scored.
 */
@Component
public class DetectionEventConsumer {

    public static final String TOPIC = "security.risk";

    private static final Logger log = LoggerFactory.getLogger(DetectionEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RiskScoringService scoring;

    public DetectionEventConsumer(RiskScoringService scoring) {
        this.scoring = scoring;
    }

    @KafkaListener(topics = TOPIC, groupId = RiskKafkaConfig.GROUP_ID,
            containerFactory = "riskListenerContainerFactory")
    public void onDetection(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            handle(record);
        } catch (Exception e) {
            log.warn("failed to process detection topic={} offset={} — {}",
                    record.topic(), record.offset(), e.getMessage());
            throw new RiskKafkaConfigRuntimeException(e);
        } finally {
            ack.acknowledge();
        }
    }

    void handle(ConsumerRecord<String, String> record) {
        JsonNode payload = parse(record.value());
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("payload is not a JSON object");
        }
        String eventType = payload.path("eventType").asText("");
        if (!"DETECTION_RAISED".equals(eventType)) {
            return;
        }
        String ruleId = payload.path("ruleId").asText(null);
        RiskSignal signal = RiskSignal.forRuleId(ruleId);
        if (signal == null) {
            log.debug("no signal mapped for ruleId={} — skipping", ruleId);
            return;
        }
        String subject = payload.path("subject").asText(null);
        String correlationId = RiskKafkaConfig.correlationId(record,
                payload.path("correlationId").asText(null));
        String eventId = payload.path("detectionId").asText(correlationId);
        String reason = payload.path("reason").asText(null);
        Instant at = instant(payload);
        scoring.onSignal(subject, record.topic(), null, signal, ruleId, reason, eventId, at);
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant instant(JsonNode payload) {
        String raw = payload.path("occurredAt").asText(null);
        if (raw != null && !raw.isBlank()) {
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException ignored) {
                // fall through to now
            }
        }
        return Instant.now();
    }

    /** Wraps processing failures so the error handler can retry/dead-letter. */
    public static class RiskKafkaConfigRuntimeException extends RuntimeException {
        public RiskKafkaConfigRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}