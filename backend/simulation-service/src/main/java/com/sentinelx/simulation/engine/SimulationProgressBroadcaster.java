package com.sentinelx.simulation.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.sentinelx.simulation.domain.SimulationStatus;
import com.sentinelx.simulation.dto.SimulationProgressDto;
import com.sentinelx.simulation.dto.SimulationRunDto;

/**
 * Broadcasts live simulation progress to registered SSE emitters. One emitter is
 * attached per HTTP client connection; a single run may have several (e.g. if a
 * dashboard reconnects). The broadcaster owns the rolling time-series so it can
 * derive per-tick deltas (events/sec, alerts over time) from the runner's
 * cumulative counters without the runner holding history.
 */
@Component
public class SimulationProgressBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SimulationProgressBroadcaster.class);

    /** Per-tick series cap retained per run (~5 min of samples). */
    static final int MAX_SERIES = 300;
    private static final long COMPLETION_TIMEOUT_MS = 30L * 60_000L;

    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Rolling per-run state used to compute deltas. */
    private static final class State {
        final List<Long> eventsPerSecond = new ArrayList<>();
        final List<Long> alertsOverTime = new ArrayList<>();
        long lastGenerated;
        long lastAlerts;
    }
    private final Map<UUID, State> state = new ConcurrentHashMap<>();

    /** Registers a client emitter for the given run. */
    public void register(UUID runId, SseEmitter emitter) {
        emitters.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(t -> {
            log.debug("sse client for simulation {} errored — {}", runId,
                    t == null ? "closed" : t.getMessage());
            remove(runId, emitter);
        });
    }

    void remove(UUID runId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(runId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(runId);
            }
        }
    }

    /** @return true if at least one client is currently listening for {@code runId}. */
    public boolean hasListeners(UUID runId) {
        Set<SseEmitter> set = emitters.get(runId);
        return set != null && !set.isEmpty();
    }

    /**
     * Publishes a live {@code progress} event to every subscriber. The runner
     * passes cumulative counters; this component turns them into per-tick rates
     * and updates the rolling series.
     */
    public void broadcastProgress(UUID runId, UUID id, SimulationStatus status,
                                  Instant startedAt, int durationSeconds,
                                  long generated, long processed,
                                  long detections, long riskDecisions,
                                  long alerts, long actions,
                                  List<String> errors, Map<String, Object> scenarioMetrics) {
        if (!hasListeners(runId)) {
            return;
        }
        State st = state.computeIfAbsent(runId, k -> new State());
        long elapsed = startedAt == null ? 0L
                : Duration.between(startedAt, Instant.now()).getSeconds();
        double progress = durationSeconds <= 0 ? 1.0
                : Math.min(1.0, (double) elapsed / durationSeconds);
        long epsDelta = Math.max(0L, generated - st.lastGenerated);
        long alertsDelta = Math.max(0L, alerts - st.lastAlerts);
        if (st.eventsPerSecond.size() >= MAX_SERIES) {
            st.eventsPerSecond.remove(0);
            st.alertsOverTime.remove(0);
        }
        st.eventsPerSecond.add(epsDelta);
        st.alertsOverTime.add(alertsDelta);
        st.lastGenerated = generated;
        st.lastAlerts = alerts;

        long[] counters = counters(scenarioMetrics);
        SimulationProgressDto dto = new SimulationProgressDto(
                id, status.name(), elapsed, progress,
                counters[0], counters[1], counters[2], counters[3], counters[4],
                                detections, riskDecisions, alerts, actions,
                new ArrayList<>(errors), epsDelta,
                riskDistribution(riskDecisions, alerts),
                new ArrayList<>(st.eventsPerSecond),
                new ArrayList<>(st.alertsOverTime));
        sendAll(runId, dto, "progress");
    }

    /**
     * Publishes the terminal {@code done} event with final counters, then
     * completes every emitter for the run so the event stream closes cleanly.
     */
    public void broadcastTerminal(UUID runId, UUID id, SimulationStatus status,
                                  Instant startedAt, long generated, long processed,
                                  long detections, long riskDecisions,
                                  long alerts, long actions,
                                  List<String> errors, Map<String, Object> scenarioMetrics) {
        long[] counters = counters(scenarioMetrics);
        long elapsed = startedAt == null ? 0L
                : Duration.between(startedAt, Instant.now()).getSeconds();
        SimulationProgressDto dto = new SimulationProgressDto(
                id, status.name(), elapsed, 1.0,
                counters[0], counters[1], counters[2], counters[3], counters[4],
                detections, riskDecisions, alerts, actions,
                new ArrayList<>(errors), 0L,
                riskDistribution(riskDecisions, alerts),
                Collections.emptyList(), Collections.emptyList());
        boolean delivered = sendAll(runId, dto, "done");
        state.remove(runId);
        if (delivered) {
            completeAll(runId);
        }
        emitters.remove(runId);
    }

    /**
     * Builds a terminal snapshot from a persisted run DTO, for clients that
     * connect after the run already finished (the broadcaster holds no emitters
     * for finished runs).
     */
    public SimulationProgressDto terminalSnapshot(UUID id, SimulationRunDto run) {
        long[] counters = counters(asMap(run.metrics()));
        long elapsed = (run.startedAt() == null || run.completedAt() == null) ? 0L
                : Duration.between(run.startedAt(), run.completedAt()).getSeconds();
        return new SimulationProgressDto(
                id, run.status(), elapsed, 1.0,
                counters[0], counters[1], counters[2], counters[3], counters[4],
                run.detections(), run.riskDecisions(), run.alerts(), run.actions(),
                new ArrayList<>(run.errors()), 0L,
                riskDistribution(run.riskDecisions(), run.alerts()),
                Collections.emptyList(), Collections.emptyList());
    }

    private boolean sendAll(UUID runId, SimulationProgressDto dto, String event) {
        Set<SseEmitter> set = emitters.get(runId);
        if (set == null || set.isEmpty()) {
            return false;
        }
        List<SseEmitter> snapshot = new ArrayList<>(set);
        for (SseEmitter e : snapshot) {
            try {
                e.send(SseEmitter.event().name(event).data(dto));
            } catch (Exception ex) {
                log.debug("dropping sse emitter for simulation {} — {}", runId, ex.getMessage());
                remove(runId, e);
            }
        }
        return true;
    }

    private void completeAll(UUID runId) {
        Set<SseEmitter> set = emitters.get(runId);
        if (set == null) {
            return;
        }
        for (SseEmitter e : new ArrayList<>(set)) {
            try {
                e.complete();
            } catch (Exception ignore) {
            }
        }
    }

    /** [users, transactions, apiRequests, networkEvents, securityEvents] from the scenario map. */
    private static long[] counters(Map<String, Object> m) {
        if (m == null) {
            return new long[5];
        }
        return new long[] {
                get(m, "targetUsers", get(m, "users", 0)),
                get(m, "totalPayments", get(m, "transactions", 0)),
                get(m, "totalRequests", get(m, "apiRequests", 0)),
                get(m, "totalEvents", get(m, "networkEvents", 0)),
                get(m, "detections", 0) // securityEvents ≈ events escalated to detections
        };
    }

    private static long get(Map<String, Object> m, String key, long fb) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(String.valueOf(v).trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return fb;
    }

    /** Coarse risk bucketing: HIGH = alerts, MEDIUM = risk decisions beyond alerts, LOW = 0. */
    private static Map<String, Long> riskDistribution(long riskDecisions, long alerts) {
        long high = alerts;
        long medium = Math.max(0L, riskDecisions - alerts);
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("HIGH", high);
        d.put("MEDIUM", medium);
        d.put("LOW", 0L);
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object metrics) {
        return metrics instanceof Map ? (Map<String, Object>) metrics : Collections.emptyMap();
    }

    /** Builds an SSE emitter with the configured idle completion timeout. */
    public static SseEmitter newEmitter() {
        return new SseEmitter(COMPLETION_TIMEOUT_MS);
    }
}

