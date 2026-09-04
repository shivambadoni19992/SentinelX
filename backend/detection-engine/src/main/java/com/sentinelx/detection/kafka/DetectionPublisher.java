package com.sentinelx.detection.kafka;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;

/**
 * Publishes raised detections as JSON to {@code security.risk}, carrying the
 * originating event's correlation id both in the payload and as a Kafka
 * header so downstream services can trace detection back to the triggering
 * request. The message key is the rule id so detections of the same rule
 * stay ordered within a partition.
 */
@Component
public class DetectionPublisher {

    public static final String TOPIC = "security.risk";
    public static final String EVENT_TYPE = "DETECTION_RAISED";

    private static final Logger log = LoggerFactory.getLogger(DetectionPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DetectionPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Publishes one detection; never throws. */
    public void publish(String sourceTopic, String subject, String correlationId,
                        DetectionResult result, Instant occurredAt) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", EVENT_TYPE);
            payload.put("ruleId", result.ruleId());
            payload.put("severity", result.severity().name());
            payload.put("riskContribution", result.riskContribution());
            payload.put("reason", result.reason());
            payload.put("recommendedAction", result.recommendedAction());
            payload.put("sourceTopic", sourceTopic);
            payload.put("subject", subject);
            payload.put("occurredAt", occurredAt == null ? Instant.now().toString() : occurredAt.toString());
            payload.put("detectionId", UUID.randomUUID().toString());
            payload.put("correlationId", correlationId);

            kafkaTemplate.send(TOPIC, result.ruleId(), Json.write(payload))
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.warn("async publish failed for rule {} — {}", result.ruleId(), ex.getMessage());
                        }
                    });
            log.info("detection raised rule={} severity={} risk={} sourceTopic={} subject={} correlationId={}",
                    result.ruleId(), result.severity(), result.riskContribution(),
                    sourceTopic, subject, correlationId);
        } catch (Exception e) {
            log.warn("failed to publish detection rule={} — {}", result.ruleId(), e.getMessage());
        }
    }

    /** Severity ordering helper for aggregation on the consumer side. */
    public static int severityRank(Severity severity) {
        return switch (severity) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case CRITICAL -> 3;
        };
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
