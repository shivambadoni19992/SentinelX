package com.sentinelx.simulation.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Observes what the real pipeline produces for simulated events. The
 * simulation itself never creates alerts; instead this consumer reads the
 * downstream topics the other services publish to and attributes each record
 * back to a run via the {@code correlationId} the simulation stamped on its
 * source events:
 *
 * <ul>
 *   <li>{@code security.risk} — detections raised by the detection engine.</li>
 *   <li>{@code security.alert} — risk decisions from the risk engine; alert
 *       records (eventType {@code ALERT_RAISED} or an {@code alertId} field)
 *       bump the alert counter, and any response actions listed on the record
 *       bump the action counter.</li>
 * </ul>
 *
 * Counters live in an in-memory registry that the runner flushes to the
 * database while the run is active.
 */
@Component
public class DownstreamTracker {

    private static final Logger log = LoggerFactory.getLogger(DownstreamTracker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Mutable counters for one active run. */
    public static final class Metrics {
        final AtomicLong detections = new AtomicLong();
        final AtomicLong riskDecisions = new AtomicLong();
        final AtomicLong alerts = new AtomicLong();
        final AtomicLong actions = new AtomicLong();

        public long detections() {
            return detections.get();
        }

        public long riskDecisions() {
            return riskDecisions.get();
        }

        public long alerts() {
            return alerts.get();
        }

        public long actions() {
            return actions.get();
        }
    }

    private final Map<String, UUID> correlationIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Metrics> runMetrics = new ConcurrentHashMap<>();

    /** Registers the correlation ids a run is about to emit. */
    public void register(UUID runId, List<String> correlationIds) {
        Metrics metrics = runMetrics.computeIfAbsent(runId, id -> new Metrics());
        for (String correlationId : correlationIds) {
            correlationIndex.put(correlationId, runId);
        }
        runMetrics.putIfAbsent(runId, metrics);
    }

    public Metrics metricsFor(UUID runId) {
        return runMetrics.get(runId);
    }

    /** Drops a finished run's attribution state. */
    public void release(UUID runId) {
        runMetrics.remove(runId);
        correlationIndex.values().removeIf(id -> id.equals(runId));
    }

    @KafkaListener(topics = "security.risk", groupId = "sentinelx-simulation-tracker",
            containerFactory = "trackerListenerContainerFactory")
    public void onRisk(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = parse(record.value());
            UUID runId = attribute(payload);
            if (runId != null && "DETECTION_RAISED".equals(payload.path("eventType").asText())) {
                runMetrics.get(runId).detections.incrementAndGet();
            }
        } catch (Exception e) {
            log.debug("tracker skipped security.risk record: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "security.alert", groupId = "sentinelx-simulation-tracker",
            containerFactory = "trackerListenerContainerFactory")
    public void onAlert(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = parse(record.value());
            UUID runId = attribute(payload);
            if (runId == null) {
                return;
            }
            Metrics metrics = runMetrics.get(runId);
            metrics.riskDecisions.incrementAndGet();
            JsonNode actions = payload.path("actions");
            if (actions.isArray() && !actions.isEmpty()) {
                metrics.actions.addAndGet(actions.size());
            } else if (!payload.path("recommendedAction").asText("").isBlank()) {
                metrics.actions.incrementAndGet();
            }
            if ("ALERT_RAISED".equals(payload.path("eventType").asText())
                    || !payload.path("alertId").asText("").isBlank()) {
                metrics.alerts.incrementAndGet();
            }
        } catch (Exception e) {
            log.debug("tracker skipped security.alert record: {}", e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    private UUID attribute(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        String correlationId = payload.path("correlationId").asText(null);
        return correlationId == null ? null : correlationIndex.get(correlationId);
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
