package com.sentinelx.simulation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.simulation.entity.SimulationRun;

/**
 * API contract for a SimulationRun. {@code simulationId} is the run's id and
 * {@code configuration} carries the SimulationConfig parameters. Counters are
 * fed by the simulation engine and the downstream tracker — the API never
 * creates alerts directly.
 */
public record SimulationRunDto(
        UUID simulationId,
        String type,
        Map<String, Object> configuration,
        String status,
        Instant startedAt,
        Instant completedAt,
        long eventsGenerated,
        long eventsProcessed,
        long detections,
        long riskDecisions,
        long alerts,
        long actions,
        List<String> errors,
        String name,
        String description,
        String runBy,
        Instant createdAt,
        Instant updatedAt) {

    public static SimulationRunDto from(SimulationRun run) {
        return new SimulationRunDto(
                run.getId(), run.getType(), run.getConfiguration(), run.getStatus(),
                run.getStartedAt(), run.getCompletedAt(),
                run.getEventsGenerated(), run.getEventsProcessed(), run.getDetections(),
                run.getRiskDecisions(), run.getAlerts(), run.getActions(),
                run.getErrors(), run.getName(), run.getDescription(), run.getRunBy(),
                run.getCreatedAt(), run.getUpdatedAt());
    }
}
