package com.sentinelx.simulation.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationLimits;
import com.sentinelx.simulation.domain.SimulationStatus;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.dto.SimulationRunDto;
import com.sentinelx.simulation.engine.SimulationRunner;
import com.sentinelx.simulation.entity.SimulationRun;
import com.sentinelx.simulation.repository.SimulationRunRepository;

/**
 * Application service for simulations. Enforces the safe upper limits on the
 * configuration, the concurrency cap, and the status transitions. Runs only
 * produce source events for the real pipeline — alerts are never created
 * here.
 */
@Service
public class SimulationService {

    private final SimulationRunRepository repository;
    private final SimulationRunner runner;
    private final SimulationLimits limits;
    private final ObjectMapper mapper = new ObjectMapper();

    public SimulationService(SimulationRunRepository repository, SimulationRunner runner,
                             SimulationLimits limits) {
        this.repository = repository;
        this.runner = runner;
        this.limits = limits;
    }

    /** Creates a QUEUED run after validating the config, then starts it. */
    public SimulationRunDto create(String typeRaw, Object configurationRaw, String name, String runBy) {
        SimulationType type = parseType(typeRaw);
        SimulationConfig config = parseConfig(configurationRaw);
        config.validate(limits.limits()); // throws IllegalArgumentException with a clear message

        if (activeRuns() >= limits.maxConcurrentRuns()) {
            throw new IllegalStateException(
                    "concurrent run limit reached (" + limits.maxConcurrentRuns() + "); try again later");
        }

        SimulationRun run = new SimulationRun();
        run.setType(type.name());
        run.setConfiguration(config.toMap());
        run.setName(name == null || name.isBlank() ? type + " simulation" : name);
        run.setStatus(SimulationStatus.QUEUED.name());
        run.setRunBy(runBy);
        run = repository.save(run);

        runner.execute(run.getId(), type, config);
        return SimulationRunDto.from(run);
    }

    public List<SimulationRunDto> all() {
        return repository.findAll().stream().map(SimulationRunDto::from).toList();
    }

    public SimulationRunDto byId(UUID id) {
        return repository.findById(id).map(SimulationRunDto::from).orElse(null);
    }

    /** Cancels a QUEUED/RUNNING run. Terminal runs are rejected. */
    public SimulationRunDto cancel(UUID id) {
        SimulationRun run = repository.findById(id).orElse(null);
        if (run == null) {
            return null;
        }
        SimulationStatus status = SimulationStatus.valueOf(run.getStatus());
        if (status.isTerminal()) {
            throw new IllegalStateException("simulation " + id + " is already " + status);
        }
        runner.cancel(id);
        if (status == SimulationStatus.QUEUED && !runner.isRunning(id)) {
            run.setStatus(SimulationStatus.CANCELLED.name());
            run.setCompletedAt(Instant.now());
            repository.save(run);
        }
        return SimulationRunDto.from(run);
    }

    private long activeRuns() {
        return repository.findByStatus(SimulationStatus.RUNNING.name()).size()
                + repository.findByStatus(SimulationStatus.QUEUED.name()).size();
    }

    static SimulationType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        try {
            return SimulationType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown simulation type: " + raw);
        }
    }

    private SimulationConfig parseConfig(Object raw) {
        if (raw == null) {
            return SimulationConfig.defaults();
        }
        try {
            return mapper.convertValue(raw, SimulationConfig.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid configuration: " + e.getMessage());
        }
    }
}
