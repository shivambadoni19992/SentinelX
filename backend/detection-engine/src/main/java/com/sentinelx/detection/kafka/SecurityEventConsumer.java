package com.sentinelx.detection.kafka;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.detection.engine.DetectionEngine;
import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.WindowStore;

/**
 * Consumes every {@code security.*} event topic, records the event into the
 * shared sliding windows, runs the composable {@link DetectionEngine}, and
 * publishes matched detections to {@code security.risk}. Events produced by
 * this engine itself ({@code DETECTION_*}) are skipped to prevent feedback
 * loops.
 */
@Component
public class SecurityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventConsumer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DetectionEngine engine;
    private final WindowStore windows;
    private final DetectionPublisher publisher;
    private final RecentDetections recent;

    public SecurityEventConsumer(DetectionEngine engine, WindowStore windows,
                                 DetectionPublisher publisher, RecentDetections recent) {
        this.engine = engine;
        this.windows = windows;
        this.publisher = publisher;
        this.recent = recent;
    }

    @KafkaListener(
            topics = {
                    "security.auth", "security.payment", "security.api", "security.retail",
                    "security.network", "security.risk", "security.alert", "security.audit"
            },
            groupId = DetectionKafkaConfig.GROUP_ID,
            containerFactory = "detectionListenerContainerFactory")
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String topic = record.topic();
        try {
            JsonNode payload = parse(record.value());
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("payload is not a JSON object: "
                        + (record.value() == null ? "null" : record.value()));
            }
            String eventType = payload.path("eventType").asText("");
            if (eventType.startsWith("DETECTION")) {
                // Own output echoed back through security.risk — not a source event.
                return;
            }
            String correlationId = DetectionKafkaConfig.promoteCorrelationId(record,
                    payload.path("correlationId").asText(null));
            Instant occurredAt = DetectionContext.resolveOccurredAt(payload, Instant.now());
            Map<String, Object> data = MAPPER.convertValue(payload, Map.class);
            data.put("topic", topic);

            DetectionContext ctx = new DetectionContext(
                    topic, record.key(), correlationId, payload, occurredAt, windows);
            recordForScoping(ctx.subjectScope(), occurredAt, data);
            String ip = ctx.sourceIp();
            if (ip != null) {
                recordForScoping("ip:" + ip, occurredAt, data);
            }

            DetectionEngine.Evaluation evaluation = engine.evaluate(ctx);
            evaluation.matches().forEach(result -> {
                publisher.publish(topic, ctx.subjectKey(), correlationId, result, occurredAt);
                recent.add(new RecentDetections.Raised(Instant.now(), topic, ctx.subjectKey(),
                        correlationId, result));
            });
            if (evaluation.matches().isEmpty()) {
                log.debug("no detections topic={} eventType={} correlationId={}", topic, eventType, correlationId);
            }
        } catch (Exception e) {
            // Rethrowing lets the DefaultErrorHandler retry, then dead-letter.
            log.warn("failed to process record topic={} partition={} offset={} — {}",
                    topic, record.partition(), record.offset(), e.getMessage());
            throw new EventProcessingException(e);
        } finally {
            ack.acknowledge();
            MDC.remove(DetectionKafkaConfig.MDC_CORRELATION_ID);
        }
    }

    private void recordForScoping(String scope, Instant at, Map<String, Object> data) {
        windows.record(scope, at, data);
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** Wraps any processing failure so the error handler can retry. */
    public static class EventProcessingException extends RuntimeException {
        public EventProcessingException(Throwable cause) {
            super(cause);
        }
    }
}
