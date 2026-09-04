package com.sentinelx.simulation.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationStatus;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.entity.SimulationRun;
import com.sentinelx.simulation.repository.SimulationRunRepository;

/**
 * Drives a simulation run: rate-paced generation of realistic events that are
 * published onto the platform's real security.* Kafka topics so the actual
 * detection -> risk -> alert pipeline processes them. The runner only
 * produces source events — it never creates alerts, risk decisions or
 * response actions itself; those counters are fed by DownstreamTracker
 * observing what the pipeline did with the simulated traffic.
 */
@Service
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SimulationRunRepository repository;
    private final DownstreamTracker tracker;
    private final ConcurrentMap<UUID, Boolean> cancellations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> active = new ConcurrentHashMap<>();

    public SimulationRunner(KafkaTemplate<String, String> kafkaTemplate,
                            SimulationRunRepository repository,
                            DownstreamTracker tracker) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository = repository;
        this.tracker = tracker;
    }

    public boolean isRunning(UUID runId) {
        return active.containsKey(runId);
    }

    /** Cancels a run; takes effect at the next per-second tick. */
    public void cancel(UUID runId) {
        cancellations.put(runId, true);
    }

    /** Executes the run on the bounded async simulation executor. */
    @Async("simulationExecutor")
    public void execute(UUID runId, SimulationType type, SimulationConfig config) {
        cancellations.remove(runId);
        active.put(runId, true);
        try {
            run(runId, type, config);
        } catch (Exception e) {
            log.error("simulation {} failed — {}", runId, e.getMessage(), e);
            fail(runId, e.getMessage());
        } finally {
            active.remove(runId);
        }
    }

    private void run(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        SimulationRun run = repository.findById(runId).orElseThrow();
        run.setStatus(SimulationStatus.RUNNING.name());
        run.setStartedAt(Instant.now());
        repository.save(run);

        var population = new SimulatedEventFactory.Population(
                config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        double attackShare = type.isAttack() ? config.attackPercentage() / 100.0 : 0.0;
        int intensity = config.intensity();
        // Extra per-scenario knobs (e.g. transactionsPerSecond, normalAmount) are
        // carried through the persisted configuration map for the event factory.
        Map<String, Object> params = run.getConfiguration();

        long generated = 0;
        long processed = 0;
        List<String> errors = new ArrayList<>();

        for (long second = 0; second < config.durationSeconds(); second++) {
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors);
                tracker.release(runId);
                return;
            }
            List<String> batchCorrelations = new ArrayList<>(config.eventsPerSecond());
            for (int i = 0; i < config.eventsPerSecond(); i++) {
                var event = SimulatedEventFactory.next(runId, generated, type, population, attackShare, intensity, params);
                batchCorrelations.add(SimulatedEventFactory.correlationId(runId, generated));
                generated++;
                try {
                    kafkaTemplate.send(event.topic(), (String) event.payload().get("correlationId"),
                                    Json.write(event.payload()))
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);
                    processed++;
                } catch (Exception e) {
                    errors.add("publish failed for " + event.topic() + ": " + rootMessage(e));
                    log.warn("simulation {} publish failed: {}", runId, rootMessage(e));
                }
            }
            tracker.register(runId, batchCorrelations);

            // Flush counters so GET /api/simulations/{id} shows live progress.
            DownstreamTracker.Metrics metrics = tracker.metricsFor(runId);
            if (metrics != null) {
                persistCounters(runId, generated, processed, metrics, errors);
            }
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors);
        tracker.release(runId);
    }

    private void persistCounters(UUID runId, long generated, long processed,
                                 DownstreamTracker.Metrics metrics, List<String> errors) {
        repository.findById(runId).ifPresent(run -> {
            run.setEventsGenerated(generated);
            run.setEventsProcessed(processed);
            run.setDetections(metrics.detections());
            run.setRiskDecisions(metrics.riskDecisions());
            run.setAlerts(metrics.alerts());
            run.setActions(metrics.actions());
            run.setErrors(List.copyOf(errors));
            repository.save(run);
        });
    }

    private void finish(UUID runId, SimulationStatus status, long generated, long processed, List<String> errors) {
        DownstreamTracker.Metrics metrics = tracker.metricsFor(runId);
        repository.findById(runId).ifPresent(run -> {
            run.setStatus(status.name());
            run.setCompletedAt(Instant.now());
            run.setEventsGenerated(generated);
            run.setEventsProcessed(processed);
            if (metrics != null) {
                run.setDetections(metrics.detections());
                run.setRiskDecisions(metrics.riskDecisions());
                run.setAlerts(metrics.alerts());
                run.setActions(metrics.actions());
            }
            run.setErrors(List.copyOf(errors));
            repository.save(run);
        });
        log.info("simulation {} finished status={} generated={} processed={}",
                runId, status, generated, processed);
    }

    private void fail(UUID runId, String message) {
        repository.findById(runId).ifPresent(run -> {
            run.setStatus(SimulationStatus.FAILED.name());
            run.setCompletedAt(Instant.now());
            run.getErrors().add(message);
            repository.save(run);
        });
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    /** Minimal ordered JSON writer (same style as detection-engine). */
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
