package com.sentinelx.simulation.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.simulation.entity.SimulationRun;

/** API contract for a SimulationRun — entities stay behind the DTO boundary. */
public record SimulationRunDto(
        UUID id,
        String name,
        String description,
        String scenario,
        Map<String, Object> config,
        String status,
        Instant startedAt,
        Instant completedAt,
        String runBy,
        Instant createdAt,
        Instant updatedAt) {

    public static SimulationRunDto from(SimulationRun s) {
        return new SimulationRunDto(s.getId(), s.getName(), s.getDescription(), s.getScenario(),
                s.getConfig(), s.getStatus(), s.getStartedAt(), s.getCompletedAt(), s.getRunBy(),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    public SimulationRun toEntity() {
        SimulationRun s = new SimulationRun();
        s.setName(name);
        s.setDescription(description);
        s.setScenario(scenario);
        if (config != null) {
            s.setConfig(config);
        }
        s.setStatus(status == null ? "PENDING" : status);
        s.setStartedAt(startedAt);
        s.setCompletedAt(completedAt);
        s.setRunBy(runBy);
        return s;
    }
}