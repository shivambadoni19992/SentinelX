package com.sentinelx.simulation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live progress snapshot streamed over SSE to simulation dashboards.
 *
 * <p>Counters (detections, riskDecisions, alerts, actions) are fed by
 * {@link com.sentinelx.simulation.engine.DownstreamTracker} observing the real
 * detection → risk → alert pipeline, so they reflect what actually happened to
 * the simulated traffic — the runner never fabricates them.</p>
 *
 * <p>{@code eventsPerSecond} and {@code alertsOverTime} are rolling per-tick
 * series maintained by {@link com.sentinelx.simulation.engine.SimulationProgressBroadcaster};
 * {@code eventsPerSec} is the most recent per-tick rate.</p>
 *
 * <p>{@code riskDistribution} is a derived snapshot: {@code HIGH = alerts},
 * {@code MEDIUM = riskDecisions − alerts}, {@code LOW = 0}. It is a coarse,
 * deterministic bucketing, not severity-tagged data.</p>
 */
public record SimulationProgressDto(
        UUID id,
        String status,
        long elapsed,
        double progress,
        long users,
        long transactions,
        long apiRequests,
        long networkEvents,
        long securityEvents,
        long detections,
        long riskDecisions,
        long alerts,
        long actions,
        List<String> errors,
        long eventsPerSec,
        Map<String, Long> riskDistribution,
        List<Long> eventsPerSecond,
        List<Long> alertsOverTime) {
}
