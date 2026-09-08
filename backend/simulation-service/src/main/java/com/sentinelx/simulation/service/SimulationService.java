package com.sentinelx.simulation.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationLimits;
import com.sentinelx.simulation.domain.SimulationStatus;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.dto.SimulationRunDto;
import com.sentinelx.simulation.engine.SimulationProgressBroadcaster;
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
    private final SimulationProgressBroadcaster progressBroadcaster;
    private final SimulationLimits limits;
    private final ObjectMapper mapper = new ObjectMapper();

    public SimulationService(SimulationRunRepository repository, SimulationRunner runner,
                             SimulationProgressBroadcaster progressBroadcaster, SimulationLimits limits) {
        this.repository = repository;
        this.runner = runner;
        this.progressBroadcaster = progressBroadcaster;
        this.limits = limits;
    }

    /** Creates a QUEUED run after validating the config, then starts it. */
    public SimulationRunDto create(String typeRaw, Object configurationRaw, String name, String runBy) {
        SimulationType type = parseType(typeRaw);
        SimulationConfig config = parseConfig(configurationRaw);
        config.validate(limits.limits()); // throws IllegalArgumentException with a clear message

        // Merge the type-specific knobs (authentic targetUsers, attemptsPerSecond,
        // amounts, …) on top of the common config so the runner can drive the mix.
        Map<String, Object> parameters = new java.util.LinkedHashMap<>(config.toMap());
        Map<String, Object> raw = rawParams(configurationRaw);
        if (raw != null) {
            parameters.putAll(raw);
        }
        validateScenarioParams(type, parameters, config, limits.limits());

        if (activeRuns() >= limits.maxConcurrentRuns()) {
            throw new IllegalStateException(
                    "concurrent run limit reached (" + limits.maxConcurrentRuns() + "); try again later");
        }

        SimulationRun run = new SimulationRun();
        run.setType(type.name());
        run.setConfiguration(parameters);
        run.setName(name == null || name.isBlank() ? type + " simulation" : name);
        run.setStatus(SimulationStatus.QUEUED.name());
        run.setRunBy(runBy);
        run = repository.save(run);

        runner.execute(run.getId(), type, config);
        return SimulationRunDto.from(run);
    }

    /**
     * Scenario-specific safe-limit checks. For BRUTE_FORCE the effective rate is
     * attemptsPerSecond (every source event is an auth attempt), so the total-event
     * cap and the events/sec cap must apply to it rather than the (silently
     * unused) eventsPerSecond knob.
     */
    private void validateScenarioParams(SimulationType type, Map<String, Object> parameters,
                                        SimulationConfig config, SimulationLimits.Limits lim) {
        if (type == SimulationType.BRUTE_FORCE) {
            Object aps = parameters.get("attemptsPerSecond");
            double attemptsPerSecond = aps instanceof Number n ? n.doubleValue() : config.eventsPerSecond();
            if (attemptsPerSecond < 1 || attemptsPerSecond > lim.maxEventsPerSecond()) {
                throw new IllegalArgumentException(
                        "attemptsPerSecond must be between 1 and " + lim.maxEventsPerSecond());
            }
            long total = (long) Math.ceil(attemptsPerSecond * config.durationSeconds());
            if (total > lim.maxTotalEvents()) {
                throw new IllegalArgumentException(
                        "total auth attempts (" + total + " = attemptsPerSecond x durationSeconds) exceeds the safe limit of "
                                + lim.maxTotalEvents());
            }
            Object tu = parameters.get("targetUsers");
            if (tu instanceof Number count && (count.doubleValue() < 1 || count.doubleValue() > lim.maxUsers())) {
                throw new IllegalArgumentException(
                        "targetUsers must be between 1 and " + lim.maxUsers());
            }
        }
        if (type == SimulationType.BOT_ACTIVITY) {
            double botCount = 0;
            Object bc = parameters.get("botCount");
            if (bc instanceof Number count && count.doubleValue() >= 1) {
                botCount = count.doubleValue();
            }
            double rpsPerBot = 0;
            Object rpb = parameters.get("rpsPerBot");
            if (rpb instanceof Number count && count.doubleValue() >= 1) {
                rpsPerBot = count.doubleValue();
            }
            if (botCount >= 1 && rpsPerBot >= 1) {
                long effective = (long) Math.ceil(rpsPerBot * botCount);
                if (effective > lim.maxEventsPerSecond()) {
                    throw new IllegalArgumentException(
                            "combined bot rate (" + effective + " = rpsPerBot x botCount) exceeds the safe limit of "
                                    + lim.maxEventsPerSecond() + " req/s");
                }
                long total = (long) Math.ceil(rpsPerBot * botCount * config.durationSeconds());
                if (total > lim.maxTotalEvents()) {
                    throw new IllegalArgumentException(
                            "total bot requests (" + total + " = rpsPerBot x botCount x durationSeconds) exceeds the safe limit of "
                                    + lim.maxTotalEvents());
                }
            }
        }
        if (type == SimulationType.PORT_SCAN || type == SimulationType.CONNECTION_SPIKE
                || type == SimulationType.FAILED_CONNECTIONS || type == SimulationType.SUSPICIOUS_OUTBOUND) {
            Object cps = parameters.get("connectionsPerSecond");
            double rate = cps instanceof Number n ? n.doubleValue() : config.eventsPerSecond();
            if (rate < 1 || rate > lim.maxEventsPerSecond()) {
                throw new IllegalArgumentException(
                        "connectionsPerSecond must be between 1 and " + lim.maxEventsPerSecond());
            }
            long total = (long) Math.ceil(rate * config.durationSeconds());
            if (total > lim.maxTotalEvents()) {
                throw new IllegalArgumentException(
                        "total network events (" + total + " = connectionsPerSecond x durationSeconds) exceeds the safe limit of "
                                + lim.maxTotalEvents());
            }
            // Safety boundary: every named source/target must be a Docker Compose
            // container. Unknown names and external hosts are rejected outright.
            validateContainers(parameters.get("sourceContainers"), "sourceContainers");
            validateContainers(parameters.get("targetContainers"), "targetContainers");
        }
    }

    /** Validates a comma-separated container list against the compose topology. */
    private static void validateContainers(Object raw, String label) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return; // fall back to the topology defaults in the event factory
        }
        for (String name : String.valueOf(raw).split(",")) {
            if (!name.isBlank()) {
                com.sentinelx.simulation.domain.DockerTopology.resolve(name.trim()); // throws for unknown/external
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawParams(Object raw) {
        if (raw instanceof Map<?, ?>) {
            return new java.util.LinkedHashMap<>((Map<String, Object>) raw);
        }
        return null;
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
            // No runner is driving this run, so push the terminal event to any
            // SSE client that may already be subscribed.
            progressBroadcaster.broadcastTerminal(id, id, SimulationStatus.CANCELLED,
                    run.getStartedAt(), run.getEventsGenerated(), run.getEventsProcessed(),
                    run.getDetections(), run.getRiskDecisions(), run.getAlerts(), run.getActions(),
                    run.getErrors(), run.getMetrics());
        }
        return SimulationRunDto.from(run);
    }

            /**
     * Returns an SSE emitter that streams live {@code progress} events for the run.
     * Late clients connecting to an already-terminal run receive a single
     * {@code done} event carrying the final counters, then the stream closes.
     */
    public SseEmitter stream(UUID id) {
        SimulationRunDto run = byId(id);
        SseEmitter emitter = SimulationProgressBroadcaster.newEmitter();
        if (run == null) {
            emitter.completeWithError(new IllegalArgumentException("simulation not found: " + id));
            return emitter;
        }
        try {
            SimulationStatus status = SimulationStatus.valueOf(run.status());
            if (status.isTerminal()) {
                emitter.send(SseEmitter.event().name("done")
                        .data(progressBroadcaster.terminalSnapshot(id, run)));
                emitter.complete();
            } else {
                progressBroadcaster.register(id, emitter);
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
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
