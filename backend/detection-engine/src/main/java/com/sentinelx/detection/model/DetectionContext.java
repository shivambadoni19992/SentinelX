package com.sentinelx.detection.model;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Everything a {@code DetectionRule} needs to evaluate one Kafka event: the
 * record coordinates (topic / key / correlationId), the parsed JSON payload,
 * the event time, and access to the shared sliding-window history so rules can
 * reason about prior events of the same subject without owning state
 * themselves — which is what keeps the rule set composable.
 */
public final class DetectionContext {

    private final String topic;
    private final String key;
    private final String correlationId;
    private final JsonNode payload;
    private final Instant occurredAt;
    private final WindowStore windows;

    public DetectionContext(String topic, String key, String correlationId,
                            JsonNode payload, Instant occurredAt, WindowStore windows) {
        this.topic = topic;
        this.key = key;
        this.correlationId = correlationId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.windows = windows;
    }

    public String topic() {
        return topic;
    }

    public String key() {
        return key;
    }

    public String correlationId() {
        return correlationId;
    }

    public JsonNode payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public WindowStore windows() {
        return windows;
    }

    /** First non-blank payload text among the candidate field names. */
    public String text(String... fields) {
        for (String f : fields) {
            String v = payload.path(f).asText(null);
            if (v != null && !v.isBlank() && !"null".equals(v)) {
                return v;
            }
        }
        return null;
    }

    public String eventType() {
        String v = text("eventType", "event_type", "action", "type");
        return v == null ? "" : v;
    }

    /** Numeric payload field, or 0 when absent / not numeric. */
    public double number(String... fields) {
        for (String f : fields) {
            JsonNode n = payload.path(f);
            if (n.isNumber()) {
                return n.doubleValue();
            }
        }
        return 0d;
    }

    public String sourceIp() {
        return text("sourceIp", "ipAddress", "ip", "clientIp");
    }

    public String deviceId() {
        return text("deviceId", "device", "deviceFingerprint");
    }

    /** The acting subject of the event, used as the default windowing scope. */
    public String subjectKey() {
        String v = text("username", "userId", "customerId", "actor", "sessionId");
        if (v != null) {
            return v;
        }
        if (key != null && !key.isBlank()) {
            return key;
        }
        String ip = sourceIp();
        return ip != null ? ip : "unknown";
    }

    /** Window scope per acting subject, e.g. {@code subject:alice}. */
    public String subjectScope() {
        return "subject:" + subjectKey();
    }

    /** Window scope per source IP, falling back to the subject scope. */
    public String ipScope() {
        String ip = sourceIp();
        return ip != null ? "ip:" + ip : subjectScope();
    }

    /** Events recorded for the scope within the trailing window (inclusive of this event). */
    public List<WindowStore.Entry> window(String scope, Duration window) {
        return windows.since(scope, occurredAt.minus(window));
    }

    /** Count of events in the trailing window matching the filter. */
    public long countInWindow(String scope, Duration window, Predicate<Map<String, Object>> filter) {
        return window(scope, window).stream().filter(e -> filter.test(e.data())).count();
    }

    /** Distinct values of one payload field within the trailing window. */
    public Set<String> distinctInWindow(String scope, Duration window, String field,
                                        Predicate<Map<String, Object>> filter) {
        return window(scope, window).stream()
                .filter(e -> filter.test(e.data()))
                .map(e -> asText(e.data().get(field)))
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Resolves the event time from the payload, falling back to ingest time.
     * Called by the consumer before the context is built.
     */
    public static Instant resolveOccurredAt(JsonNode payload, Instant fallback) {
        for (String f : new String[]{"occurredAt", "createdAt", "timestamp", "eventTime"}) {
            String v = payload.path(f).asText(null);
            if (v != null && !v.isBlank()) {
                try {
                    return Instant.parse(v);
                } catch (DateTimeParseException ignored) {
                    // fall through to the next candidate field
                }
            }
        }
        return fallback;
    }

    static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
