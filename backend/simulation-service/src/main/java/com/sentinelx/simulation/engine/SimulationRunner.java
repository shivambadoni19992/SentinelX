package com.sentinelx.simulation.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private final SimulationProgressBroadcaster progressBroadcaster;
    private final ConcurrentMap<UUID, Boolean> cancellations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> active = new ConcurrentHashMap<>();

    public SimulationRunner(KafkaTemplate<String, String> kafkaTemplate,
                            SimulationRunRepository repository,
                            DownstreamTracker tracker,
                            SimulationProgressBroadcaster progressBroadcaster) {
        this.kafkaTemplate = kafkaTemplate;
        this.repository = repository;
        this.tracker = tracker;
        this.progressBroadcaster = progressBroadcaster;
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

        // PAYMENT_FRAUD drives its own dedicated generator with the full parameter set.
        if (type == SimulationType.PAYMENT_FRAUD) {
            paymentFraud(runId, type, config);
            return;
        }
        // TRANSACTION_VELOCITY drives its own dedicated generator for rapid payment bursts.
        if (type == SimulationType.TRANSACTION_VELOCITY) {
            transactionVelocity(runId, type, config);
            return;
        }
        // API_ABUSE drives its own dedicated generator for request-flood drills.
        if (type == SimulationType.API_ABUSE) {
            apiAbuse(runId, type, config);
            return;
        }
        // BOT_ACTIVITY drives its own dedicated generator that mixes a bot fleet
        // with legitimate users.
        if (type == SimulationType.BOT_ACTIVITY) {
            botActivity(runId, type, config);
            return;
        }
        // NETWORK_* drills drive a dedicated synthetic-observation generator that
        // only ever targets Docker Compose containers (see DockerTopology).
        if (type == SimulationType.PORT_SCAN || type == SimulationType.CONNECTION_SPIKE
                || type == SimulationType.FAILED_CONNECTIONS || type == SimulationType.SUSPICIOUS_OUTBOUND) {
            networkSimulation(runId, type, config);
            return;
        }

        var population = new SimulatedEventFactory.Population(
                config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        double attackShare = type.isAttack() ? config.attackPercentage() / 100.0 : 0.0;
        int intensity = config.intensity();
        // Extra per-scenario knobs (e.g. transactionsPerSecond, normalAmount,
        // attemptsPerSecond) are carried through the persisted configuration map.
        Map<String, Object> params = run.getConfiguration();
        // For BRUTE_FORCE every generated event is an auth attempt, so the
        // configured attemptsPerSecond is the effective per-second rate (validated
        // against the safe limits by SimulationService).
        int eps = type == SimulationType.BRUTE_FORCE
                ? Math.max(1, (int) Math.round(paramDouble(params, "attemptsPerSecond", config.eventsPerSecond())))
                : config.eventsPerSecond();

        long failedLogins = 0;
        long successfulLogins = 0;
        long newIpLogins = 0;
        long newDeviceLogins = 0;
        long suspiciousPayments = 0;
        long totalPayments = 0;
        long highValuePayments = 0;
        long declinedPayments = 0;
        long newDevicePayments = 0;
        long suspiciousIpPayments = 0;
        java.util.Set<String> sourceIps = new java.util.HashSet<>();
        java.util.Set<String> targets = new java.util.HashSet<>();
        java.util.Set<String> fraudCards = new java.util.HashSet<>();

        long generated = 0;
        long processed = 0;
        List<String> errors = new ArrayList<>();

        for (long second = 0; second < config.durationSeconds(); second++) {
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors,
                        scenarioMetrics(failedLogins, successfulLogins, newIpLogins, newDeviceLogins,
                                suspiciousPayments, totalPayments, highValuePayments, declinedPayments,
                                newDevicePayments, suspiciousIpPayments, sourceIps, targets, fraudCards,
                                eps, type));
                tracker.release(runId);
                return;
            }
            List<String> batchCorrelations = new ArrayList<>(eps);
            for (int i = 0; i < eps; i++) {
                var event = SimulatedEventFactory.next(runId, generated, type, population, attackShare, intensity, params);
                batchCorrelations.add(SimulatedEventFactory.correlationId(runId, generated));
                generated++;
                String eventType = String.valueOf(event.payload().get("eventType"));
                String sourceIp = String.valueOf(event.payload().get("sourceIp"));
                String actor = String.valueOf(event.payload().get("actor"));
                Object amount = event.payload().get("amount");
                double amt = amount instanceof Number n ? n.doubleValue() : 0;
                if ("LOGIN_FAILED".equals(eventType)) {
                    failedLogins++;
                } else if ("LOGIN_SUCCESS".equals(eventType)) {
                    successfulLogins++;
                    if (sourceIp != null && sourceIp.startsWith("198.51.100.")) {
                        newIpLogins++;
                    }
                    Object newDevice = event.payload().get("deviceId");
                    if (actor != null && actor.startsWith("victim") && newDevice != null) {
                        String expectedNormal = java.util.UUID.nameUUIDFromBytes(
                                ("device:" + (Math.floorMod(actor.hashCode(), 97))).getBytes()).toString();
                        if (!expectedNormal.equals(newDevice.toString())) {
                            newDeviceLogins++;
                        }
                    }
                }
                if ("PAYMENT_AUTHORIZED".equals(eventType) && amt >= 1000) {
                    suspiciousPayments++;
                }
                // Payment-fraud tracking: count payments, high-value, declined,
                // new-device and suspicious-IP attributes.
                if ("PAYMENT_CREATED".equals(eventType) || "PAYMENT_AUTHORIZED".equals(eventType)) {
                    totalPayments++;
                    if (amt >= 1000) {
                        highValuePayments++;
                    }
                    Object outcome = event.payload().get("outcome");
                    if ("DECLINED".equals(outcome)) {
                        declinedPayments++;
                    }
                    Object newDevice = event.payload().get("newDevice");
                    if (Boolean.TRUE.equals(newDevice) || "true".equals(String.valueOf(newDevice))) {
                        newDevicePayments++;
                    }
                    Object suspiciousIp = event.payload().get("suspiciousIp");
                    if (Boolean.TRUE.equals(suspiciousIp) || "true".equals(String.valueOf(suspiciousIp))) {
                        suspiciousIpPayments++;
                    }
                    if (actor != null && actor.startsWith("fraudcard")) {
                        fraudCards.add(actor);
                    }
                }
                if (sourceIp != null && !sourceIp.equals("null")) {
                    sourceIps.add(sourceIp);
                }
                if (actor != null && actor.startsWith("victim")) {
                    targets.add(actor);
                }
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
                persistCounters(runId, generated, processed, metrics, errors,
                        scenarioMetrics(failedLogins, successfulLogins, newIpLogins, newDeviceLogins,
                                suspiciousPayments, totalPayments, highValuePayments, declinedPayments,
                                newDevicePayments, suspiciousIpPayments, sourceIps, targets, fraudCards,
                                eps, type));
            }
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors,
                scenarioMetrics(failedLogins, successfulLogins, newIpLogins, newDeviceLogins,
                        suspiciousPayments, totalPayments, highValuePayments, declinedPayments,
                        newDevicePayments, suspiciousIpPayments, sourceIps, targets, fraudCards,
                        eps, type));
        tracker.release(runId);
    }

    private void paymentFraud(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        Map<String, Object> params = repository.findById(runId).orElseThrow().getConfiguration();
        double highValueAmount = paramDouble(params, "highValueAmount", 9_500);
        int totalTransactions = (int) paramDouble(params, "transactions", 10_000);
        double attackShare = config.attackPercentage() / 100.0;
        int intensity = config.intensity();
        var population = new SimulatedEventFactory.Population(config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        long[] tp = {0}, hv = {0}, dp = {0}, nd = {0}, si = {0};
        java.util.Set<String> fraudCards = new java.util.HashSet<>();
        java.util.Set<String> sourceIps = new java.util.HashSet<>();
        long generated = 0, processed = 0;
        List<String> errors = new ArrayList<>();
        int duration = config.durationSeconds();
        long total = Math.min(totalTransactions, (long) duration * config.eventsPerSecond());
        long rate = Math.max(1, total / Math.max(1, duration));
        long flush = Math.max(1, total / 20);
        for (long sec = 0; sec < duration && generated < total; sec++) {
            if (cancellations.containsKey(runId)) { finish(runId, SimulationStatus.CANCELLED, generated, processed, errors, scenarioMetrics(0, 0, 0, 0, 0, tp[0], hv[0], dp[0], nd[0], si[0], sourceIps, new java.util.HashSet<>(), fraudCards, (int) (attackShare * 100), type)); tracker.release(runId); return; }
            List<String> batch = new ArrayList<>((int) rate);
            long target = Math.min(rate, total - generated);
            for (int i = 0; i < target; i++) {
                boolean hostile = ThreadLocalRandom.current().nextDouble() < attackShare;
                var ev = SimulatedEventFactory.paymentFraudEvent(runId, generated, population, intensity, hostile, params);
                batch.add(SimulatedEventFactory.correlationId(runId, generated));
                generated++;
                pfTrack(ev.payload(), highValueAmount, tp, hv, dp, nd, si, fraudCards, sourceIps);
            }
            pfPublish(runId, population, intensity, attackShare, params, generated, batch, errors);
            processed = batch.size();
            tracker.register(runId, batch);
            DownstreamTracker.Metrics m = tracker.metricsFor(runId);
            if (m != null && (generated % flush == 0 || sec == duration - 1)) persistCounters(runId, generated, processed, m, errors, scenarioMetrics(0, 0, 0, 0, 0, tp[0], hv[0], dp[0], nd[0], si[0], sourceIps, new java.util.HashSet<>(), fraudCards, (int) (attackShare * 100), type));
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors, scenarioMetrics(0, 0, 0, 0, 0, tp[0], hv[0], dp[0], nd[0], si[0], sourceIps, new java.util.HashSet<>(), fraudCards, (int) (attackShare * 100), type));
        tracker.release(runId);
    }

    /**
     * Drives the TRANSACTION_VELOCITY scenario: rapid payment bursts from a small
     * set of concurrent users that far exceed normal human transaction rates.
     */
    private void transactionVelocity(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        Map<String, Object> params = repository.findById(runId).orElseThrow().getConfiguration();
        double amount = paramDouble(params, "amount", 500);
        int concurrentUsers = Math.max(1, (int) paramDouble(params, "concurrentUsers", 5));
        int txnPerUser = Math.max(1, (int) paramDouble(params, "transactionsPerUser", 20));
        int timeWindowSeconds = Math.max(1, (int) paramDouble(params, "timeWindowSeconds", 60));
        double attackShare = config.attackPercentage() / 100.0;
        int intensity = config.intensity();
        var population = new SimulatedEventFactory.Population(config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        long[] totalPayments = {0}, burstPayments = {0}, baselinePayments = {0}, heldPayments = {0};
        java.util.Set<String> affectedUsers = new java.util.HashSet<>();
        java.util.Set<String> sourceIps = new java.util.HashSet<>();
        long generated = 0, processed = 0;
        List<String> errors = new ArrayList<>();
        int duration = config.durationSeconds();
        long attackVelocity = Math.max(1, (long) concurrentUsers * txnPerUser / Math.max(1, timeWindowSeconds));
        long baselineRate = Math.max(1, config.eventsPerSecond() / 10);
        long flush = Math.max(1, (attackVelocity + baselineRate) * duration / 20);
        for (long sec = 0; sec < duration; sec++) {
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors, velocityMetrics(totalPayments[0], baselinePayments[0], burstPayments[0], affectedUsers, heldPayments[0], baselineRate, attackVelocity, 0));
                tracker.release(runId);
                return;
            }
            boolean burstPhase = ThreadLocalRandom.current().nextDouble() < attackShare;
            long rate = burstPhase ? attackVelocity : baselineRate;
            List<String> batch = new ArrayList<>((int) Math.min(rate, 1000));
            for (int i = 0; i < rate; i++) {
                boolean hostile = burstPhase && ThreadLocalRandom.current().nextDouble() < attackShare;
                var ev = SimulatedEventFactory.transactionVelocityEvent(runId, generated, population, intensity, hostile, params);
                batch.add(SimulatedEventFactory.correlationId(runId, generated));
                generated++;
                if ("PAYMENT_CREATED".equals(ev.payload().get("eventType"))) {
                    totalPayments[0]++;
                    String actor = String.valueOf(ev.payload().get("actor"));
                    if (hostile) { burstPayments[0]++; affectedUsers.add(actor); }
                    else { baselinePayments[0]++; }
                    String sourceIp = String.valueOf(ev.payload().get("sourceIp"));
                    if (!"null".equals(sourceIp)) sourceIps.add(sourceIp);
                }
            }
            tvPublish(runId, population, intensity, attackShare, params, generated, batch, errors);
            processed = batch.size();
            tracker.register(runId, batch);
            DownstreamTracker.Metrics m = tracker.metricsFor(runId);
            if (m != null && (generated % flush == 0 || sec == duration - 1)) {
                heldPayments[0] = m.actions();
                persistCounters(runId, generated, processed, m, errors, velocityMetrics(totalPayments[0], baselinePayments[0], burstPayments[0], affectedUsers, heldPayments[0], baselineRate, attackVelocity, m.riskDecisions()));
            }
            Thread.sleep(1000);
        }
        DownstreamTracker.Metrics finalMetrics = tracker.metricsFor(runId);
        long finalRisk = finalMetrics != null ? finalMetrics.riskDecisions() : 0;
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors, velocityMetrics(totalPayments[0], baselinePayments[0], burstPayments[0], affectedUsers, heldPayments[0], baselineRate, attackVelocity, finalRisk));
        tracker.release(runId);
    }

    /** Publishes a batch of transaction-velocity events to Kafka. */
    private void tvPublish(UUID runId, SimulatedEventFactory.Population population, int intensity,
                           double attackShare, Map<String, Object> params, long generated,
                           List<String> batch, List<String> errors) {
        for (int i = 0; i < batch.size(); i++) {
            boolean hostile = ThreadLocalRandom.current().nextDouble() < attackShare;
            long seq = generated - batch.size() + i;
            var event = SimulatedEventFactory.transactionVelocityEvent(runId, seq, population, intensity, hostile, params);
            try {
                kafkaTemplate.send(event.topic(), (String) event.payload().get("correlationId"),
                                Json.write(event.payload()))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add("publish failed for " + event.topic() + ": " + rootMessage(e));
                log.warn("transaction-velocity simulation {} publish failed: {}", runId, rootMessage(e));
            }
        }
    }

    /** Builds the velocity-specific metrics map for the dashboard. */
    private static Map<String, Object> velocityMetrics(long totalPayments, long baselinePayments,
                                                       long burstPayments,
                                                       java.util.Set<String> affectedUsers,
                                                       long heldPayments, long baselineRate,
                                                       long attackVelocity, long riskDecisions) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("totalPayments", totalPayments);
        m.put("baselinePayments", baselinePayments);
        m.put("burstPayments", burstPayments);
        m.put("affectedUsers", affectedUsers.size());
        m.put("heldPayments", heldPayments);
        m.put("baselineRate", baselineRate);
        m.put("attackVelocity", attackVelocity);
        m.put("risk", riskDecisions);
        return m;
    }

    /** Builds the API-abuse metrics map for the dashboard. */
    private static Map<String, Object> apiAbuseMetrics(long totalRequests, long baselineRequests,
                                                       long attackRequests, long blockedRequests,
                                                       long currentRps, long baselineRps, long attackRps,
                                                       java.util.Set<String> topIps,
                                                       java.util.Set<String> topEndpoints,
                                                       long riskDecisions) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("totalRequests", totalRequests);
        m.put("baselineRequests", baselineRequests);
        m.put("attackRequests", attackRequests);
        m.put("blockedRequests", blockedRequests);
        m.put("currentRps", currentRps);
        m.put("baselineRps", baselineRps);
        m.put("attackRps", attackRps);
        m.put("topIps", topIps.size());
        m.put("topEndpoints", topEndpoints.size());
        m.put("risk", riskDecisions);
        return m;
    }

    /**
     * Dedicated generator for the API_ABUSE scenario: replays a request flood
     * from a small set of attacker IPs against a single target endpoint at a
     * configurable attack RPS while a benign baseline runs alongside it.
     */
    private void apiAbuse(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        SimulationRun run = repository.findById(runId).orElseThrow();
        double attackShare = config.attackPercentage() / 100.0;
        int intensity = config.intensity();
        int duration = config.durationSeconds();
        // Extra per-scenario knobs (normalRps, attackRps, targetEndpoint, apiSourceIps)
        // are carried through the persisted configuration map.
        Map<String, Object> params = run.getConfiguration();

        long baselineRps = paramLong(params, "normalRps", 100);
        long attackRps = paramLong(params, "attackRps", 5_000);
        int sourceIps = (int) paramLong(params, "apiSourceIps", 15);

        log.info("api-abuse simulation {} baselineRps={} attackRps={} sourceIps={} duration={}s attackShare={}",
                runId, baselineRps, attackRps, sourceIps, duration, attackShare);

        var population = new SimulatedEventFactory.Population(config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        long[] totalRequests = {0};
        long[] baselineRequests = {0};
        long[] attackRequests = {0};
        long[] blockedRequests = {0};
        java.util.Set<String> topIps = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> topEndpoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> batch = new ArrayList<>(1_000);
        long generated = 0;
        long processed = 0;
        int flush = Math.max(1, duration / 10);

        long end = System.currentTimeMillis() + duration * 1000L;
        int tick = 0;
        while (System.currentTimeMillis() < end) {
            tick++;
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors, apiAbuseMetrics(totalRequests[0], baselineRequests[0], attackRequests[0], blockedRequests[0], attackRps, baselineRps, attackRps, topIps, topEndpoints, riskDecisions(runId)));
                tracker.release(runId);
                return;
            }
            // Attack traffic: flood requests to the target endpoint.
            long attackThisTick = Math.max(1, (long) (attackRps * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4)));
            for (long i = 0; i < attackThisTick; i++) {
                boolean hostile = ThreadLocalRandom.current().nextDouble() < attackShare;
                java.util.Map<String, Object> evParams = new java.util.HashMap<>(params);
                evParams.put("apiSourceIps", sourceIps);
                var ev = SimulatedEventFactory.apiAbuseEvent(runId, generated, population, intensity, hostile, evParams);
                publishApiEvent(runId, ev, batch, errors);
                trackApiAbuse(ev.payload(), topIps, topEndpoints, attackRequests, blockedRequests, hostile);
                generated++;
            }
            // Baseline traffic: benign requests spread across endpoints.
            long baselineThisTick = Math.max(1, (long) (baselineRps * (0.7 + ThreadLocalRandom.current().nextDouble() * 0.6)));
            for (long i = 0; i < baselineThisTick; i++) {
                var ev = SimulatedEventFactory.apiAbuseEvent(runId, generated, population, intensity, false, params);
                publishApiEvent(runId, ev, batch, errors);
                trackApiAbuse(ev.payload(), topIps, topEndpoints, baselineRequests, blockedRequests, false);
                generated++;
            }
            totalRequests[0] = attackRequests[0] + baselineRequests[0];
            processed = generated;
            tracker.register(runId, batch);
            batch.clear();
            if (tick % flush == 0) {
                persistCounters(runId, generated, processed, tracker.metricsFor(runId), errors, apiAbuseMetrics(totalRequests[0], baselineRequests[0], attackRequests[0], blockedRequests[0], attackThisTick + baselineThisTick, baselineRps, attackRps, topIps, topEndpoints, riskDecisions(runId)));
            }
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors, apiAbuseMetrics(totalRequests[0], baselineRequests[0], attackRequests[0], blockedRequests[0], attackRps + baselineRps, baselineRps, attackRps, topIps, topEndpoints, riskDecisions(runId)));
        tracker.release(runId);
    }

    private long riskDecisions(UUID runId) {
        DownstreamTracker.Metrics m = tracker.metricsFor(runId);
        return m != null ? m.riskDecisions() : 0;
    }

    /** Publishes one API-abuse event to Kafka and registers its correlation id. */
    private void publishApiEvent(UUID runId, SimulatedEventFactory.GeneratedEvent event,
                                 List<String> batch, List<String> errors) {
        try {
            kafkaTemplate.send(event.topic(), (String) event.payload().get("correlationId"),
                    Json.write(event.payload()));
            batch.add((String) event.payload().get("correlationId"));
        } catch (Exception e) {
            errors.add("publish failed for " + event.topic() + ": " + rootMessage(e));
        }
    }

    /**
     * Dedicated generator for the BOT_ACTIVITY scenario: replays a bot fleet
     * (dedicated IPs + bot user-agent) hammering the target endpoints at a
     * per-bot RPS, mixed with legitimate users whose share is driven by
     * {@code attackPercentage}. Each per-second tick publishes the whole mix
     * and flushes dashboard counters.
     */
    private void botActivity(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        SimulationRun run = repository.findById(runId).orElseThrow();
        double botShare = config.attackPercentage() / 100.0;
        int intensity = config.intensity();
        Map<String, Object> params = run.getConfiguration();
        int duration = (int) paramLong(params, "botDurationSeconds", config.durationSeconds());

        int botCount = (int) paramLong(params, "botCount", 50);
        long rpsPerBot = Math.max(1, paramLong(params, "rpsPerBot", 20));

        log.info("bot-activity simulation {} botCount={} rpsPerBot={} duration={}s botShare={}",
                runId, botCount, rpsPerBot, duration, botShare);

        var population = new SimulatedEventFactory.Population(config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        long[] legitRequests = {0};
        long[] botRequests = {0};
        java.util.Set<String> botIps = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> endpoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> batch = new ArrayList<>(1_000);
        long generated = 0;
        long processed = 0;
        int flush = Math.max(1, duration / 10);

        long end = System.currentTimeMillis() + duration * 1000L;
        int tick = 0;
        while (System.currentTimeMillis() < end) {
            tick++;
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors, botMetrics(legitRequests[0], botRequests[0], botIps, endpoints, riskDecisions(runId), actionsFor(runId)));
                tracker.release(runId);
                return;
            }
            long totalTick = Math.max(2, Math.round(rpsPerBot * botCount));
            long botTick = Math.round(totalTick * botShare);
            long legitTick = totalTick - botTick;
            // Bot fleet traffic.
            for (long i = 0; i < botTick; i++) {
                var ev = SimulatedEventFactory.botActivityEvent(runId, generated, population, intensity, true, params);
                publishApiEvent(runId, ev, batch, errors);
                trackBot(ev.payload(), botRequests, botIps, endpoints);
                generated++;
            }
            // Legitimate user traffic.
            for (long i = 0; i < legitTick; i++) {
                var ev = SimulatedEventFactory.botActivityEvent(runId, generated, population, intensity, false, params);
                publishApiEvent(runId, ev, batch, errors);
                trackBot(ev.payload(), legitRequests, botIps, endpoints);
                generated++;
            }
            processed = generated;
            tracker.register(runId, batch);
            batch.clear();
            if (tick % flush == 0) {
                persistCounters(runId, generated, processed, tracker.metricsFor(runId), errors, botMetrics(legitRequests[0], botRequests[0], botIps, endpoints, riskDecisions(runId), actionsFor(runId)));
            }
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors, botMetrics(legitRequests[0], botRequests[0], botIps, endpoints, riskDecisions(runId), actionsFor(runId)));
        tracker.release(runId);
    }

    /**
     * Dedicated generator for the NETWORK_* drills. Emits synthetic
     * network-observation events onto {@code security.network} at a paced
     * rate. Safety: source/target containers resolve exclusively through
     * {@link DockerTopology} (compose-internal, IP-limited) — the generator
     * opens no sockets and can never reach an external system.
     */
    private void networkSimulation(UUID runId, SimulationType type, SimulationConfig config) throws Exception {
        SimulationRun run = repository.findById(runId).orElseThrow();
        Map<String, Object> params = run.getConfiguration();
        int intensity = config.intensity();
        int duration = (int) paramLong(params, "networkDurationSeconds", config.durationSeconds());
        long cps = Math.max(1, paramLong(params, "connectionsPerSecond", 30));

        log.info("network simulation {} type={} connectionsPerSecond={} duration={}s (synthetic events only, compose-internal targets)",
                runId, type, cps, duration);

        var population = new SimulatedEventFactory.Population(config.numberOfUsers(), config.numberOfDevices(), config.numberOfIpAddresses());
        long[] totalEvents = {0};
        java.util.Set<String> sources = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> destinations = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> ports = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> protocols = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Map<String, Long> flowCounts = new java.util.concurrent.ConcurrentHashMap<>();
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> batch = new ArrayList<>(1_000);
        long generated = 0;
        long processed = 0;
        int flush = Math.max(1, duration / 10);

        long end = System.currentTimeMillis() + duration * 1000L;
        int tick = 0;
        while (System.currentTimeMillis() < end) {
            tick++;
            if (cancellations.containsKey(runId)) {
                finish(runId, SimulationStatus.CANCELLED, generated, processed, errors, networkMetrics(totalEvents[0], cps, cps, duration, sources, destinations, ports, protocols, flowCounts, riskDecisions(runId), actionsFor(runId)));
                tracker.release(runId);
                return;
            }
            long thisTick = Math.max(1, Math.round(cps * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4)));
            for (long i = 0; i < thisTick; i++) {
                var ev = SimulatedEventFactory.networkObservationEvent(runId, generated, population, type, intensity, params);
                publishApiEvent(runId, ev, batch, errors);
                trackNetwork(ev.payload(), totalEvents, sources, destinations, ports, protocols, flowCounts);
                generated++;
            }
            processed = generated;
            tracker.register(runId, batch);
            batch.clear();
            if (tick % flush == 0) {
                persistCounters(runId, generated, processed, tracker.metricsFor(runId), errors, networkMetrics(totalEvents[0], thisTick, cps, duration, sources, destinations, ports, protocols, flowCounts, riskDecisions(runId), actionsFor(runId)));
            }
            Thread.sleep(1000);
        }
        finish(runId, SimulationStatus.COMPLETED, generated, processed, errors, networkMetrics(totalEvents[0], cps, cps, duration, sources, destinations, ports, protocols, flowCounts, riskDecisions(runId), actionsFor(runId)));
        tracker.release(runId);
    }

    /** Tracks API-abuse counters from a generated event payload. */
    private static void trackApiAbuse(Map<String, Object> payload, java.util.Set<String> topIps,
                                      java.util.Set<String> topEndpoints, long[] counter,
                                      long[] blocked, boolean hostile) {
        counter[0]++;
        if ("DENIED".equals(payload.get("outcome"))) {
            blocked[0]++;
        }
        String ip = String.valueOf(payload.get("sourceIp"));
        if (hostile && !ip.isBlank()) {
            topIps.add(ip);
        }
        String path = String.valueOf(payload.get("path"));
        if (!path.isBlank() && !"null".equals(path)) {
            topEndpoints.add(path);
        }
    }

    /** Builds the bot-activity metrics map for the dashboard. */
    private static Map<String, Object> botMetrics(long legitRequests, long botRequests,
                                                  java.util.Set<String> botIps,
                                                  java.util.Set<String> endpoints,
                                                  long riskDecisions, long actions) {
        long total = legitRequests + botRequests;
        int legitPct = total == 0 ? 0 : (int) Math.round(100.0 * legitRequests / total);
        int botPct = 100 - legitPct;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("legitRequests", legitRequests);
        m.put("botRequests", botRequests);
        m.put("totalRequests", total);
        m.put("legitimatePct", legitPct);
        m.put("botPct", botPct);
        m.put("botIps", botIps.size());
        m.put("endpoints", endpoints.size());
        m.put("risk", riskDecisions);
        m.put("actions", actions);
        return m;
    }

    private long actionsFor(UUID runId) {
        DownstreamTracker.Metrics m = tracker.metricsFor(runId);
        return m != null ? m.actions() : 0;
    }

    /** Tracks bot-activity counters from a generated event payload. */
    private static void trackBot(Map<String, Object> payload, long[] counter,
                                 java.util.Set<String> botIps,
                                 java.util.Set<String> endpoints) {
        counter[0]++;
        if (Boolean.TRUE.equals(payload.get("botRequest"))) {
            String ip = String.valueOf(payload.get("sourceIp"));
            if (!ip.isBlank() && !"null".equals(ip)) {
                botIps.add(ip);
            }
        }
        String path = String.valueOf(payload.get("path"));
        if (!path.isBlank() && !"null".equals(path)) {
            endpoints.add(path);
        }
    }

    /** Builds the network-drill metrics map for the dashboard. */
    private static Map<String, Object> networkMetrics(long totalEvents, long currentRate,
                                                      long configuredRate, int durationSeconds,
                                                      java.util.Set<String> sources,
                                                      java.util.Set<String> destinations,
                                                      java.util.Set<String> ports,
                                                      java.util.Set<String> protocols,
                                                      Map<String, Long> flowCounts,
                                                      long riskDecisions, long actions) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("totalEvents", totalEvents);
        m.put("currentRate", currentRate);
        m.put("connectionsPerSecond", configuredRate);
        m.put("distinctSources", sources.size());
        m.put("distinctDestinations", destinations.size());
        m.put("distinctPorts", ports.size());
        m.put("protocols", new java.util.ArrayList<>(protocols));
        // Top flows for the dashboard table: Source / Destination / Port /
        // Protocol / Rate / Risk / Severity, capped at 25 rows.
        List<Map<String, Object>> flows = flowCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(25)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", -1);
                    long count = e.getValue();
                    String severity = count >= 100 ? "HIGH" : count >= 30 ? "MEDIUM" : "LOW";
                    Map<String, Object> f = new java.util.LinkedHashMap<>();
                    f.put("source", parts.length > 0 ? parts[0] : "");
                    f.put("destination", parts.length > 1 ? parts[1] : "");
                    f.put("port", parts.length > 2 ? parts[2] : "");
                    f.put("protocol", parts.length > 3 ? parts[3] : "");
                    f.put("rate", durationSeconds > 0 ? Math.round((double) count / durationSeconds) : count);
                    f.put("risk", severity.equals("HIGH") ? 55 : severity.equals("MEDIUM") ? 25 : 10);
                    f.put("severity", severity);
                    f.put("count", count);
                    return f;
                })
                .toList();
        m.put("flows", flows);
        m.put("risk", riskDecisions);
        m.put("actions", actions);
        return m;
    }

    /** Tracks network-drill counters from a generated event payload. */
    private static void trackNetwork(Map<String, Object> payload, long[] counter,
                                     java.util.Set<String> sources,
                                     java.util.Set<String> destinations,
                                     java.util.Set<String> ports,
                                     java.util.Set<String> protocols,
                                     Map<String, Long> flowCounts) {
        counter[0]++;
        String source = String.valueOf(payload.getOrDefault("sourceContainer", payload.get("sourceIp")));
        String destination = String.valueOf(payload.getOrDefault("destinationContainer",
                payload.get("destinationIp")));
        String port = String.valueOf(payload.get("destinationPort"));
        String protocol = String.valueOf(payload.getOrDefault("protocol", "TCP"));
        if (!source.isBlank() && !"null".equals(source)) {
            sources.add(source);
        }
        if (!destination.isBlank() && !"null".equals(destination)) {
            destinations.add(destination);
        }
        if (!port.isBlank() && !"null".equals(port)) {
            ports.add(port);
        }
        protocols.add(protocol);
        flowCounts.merge(source + "|" + destination + "|" + port + "|" + protocol, 1L, Long::sum);
    }

    /** Tracks payment-fraud counters from a generated event payload. */
    private static void pfTrack(Map<String, Object> payload, double highValueAmount, long[] tp, long[] hv, long[] dp, long[] nd, long[] si, java.util.Set<String> fraudCards, java.util.Set<String> sourceIps) {
        if (!"PAYMENT_CREATED".equals(payload.get("eventType"))) return;
        tp[0]++;
        double amt = payload.get("amount") instanceof Number n ? n.doubleValue() : 0;
        if (amt >= highValueAmount * 0.8) hv[0]++;
        if ("DECLINED".equals(payload.get("outcome"))) dp[0]++;
        if (Boolean.TRUE.equals(payload.get("newDevice"))) nd[0]++;
        if (Boolean.TRUE.equals(payload.get("suspiciousIp"))) si[0]++;
        String actor = String.valueOf(payload.get("actor"));
        if (actor.startsWith("fraudcard")) fraudCards.add(actor);
        String sourceIp = String.valueOf(payload.get("sourceIp"));
        if (!"null".equals(sourceIp)) sourceIps.add(sourceIp);
    }

    /** Publishes a batch of payment-fraud events to Kafka. */
    private void pfPublish(UUID runId, SimulatedEventFactory.Population population, int intensity, double attackShare, Map<String, Object> params, long generated, List<String> batch, List<String> errors) {
        for (int i = 0; i < batch.size(); i++) {
            boolean hostile = ThreadLocalRandom.current().nextDouble() < attackShare;
            long seq = generated - batch.size() + i;
            var event = SimulatedEventFactory.paymentFraudEvent(runId, seq, population, intensity, hostile, params);
            try {
                kafkaTemplate.send(event.topic(), (String) event.payload().get("correlationId"), Json.write(event.payload())).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                errors.add("publish failed for " + event.topic() + ": " + rootMessage(e));
                log.warn("payment-fraud simulation {} publish failed: {}", runId, rootMessage(e));
            }
        }
    }

    private void persistCounters(UUID runId, long generated, long processed,
                                 DownstreamTracker.Metrics metrics, List<String> errors,
                                 Map<String, Object> scenarioMetrics) {
        repository.findById(runId).ifPresent(run -> {
            run.setEventsGenerated(generated);
            run.setEventsProcessed(processed);
            run.setDetections(metrics.detections());
            run.setRiskDecisions(metrics.riskDecisions());
            run.setAlerts(metrics.alerts());
            run.setActions(metrics.actions());
            run.setErrors(List.copyOf(errors));
                        run.setMetrics(scenarioMetrics);
            repository.save(run);
            progressBroadcaster.broadcastProgress(runId, runId, SimulationStatus.RUNNING,
                    run.getStartedAt(), progressDuration(run.getConfiguration()),
                    generated, processed,
                    metrics.detections(), metrics.riskDecisions(), metrics.alerts(), metrics.actions(),
                    errors, scenarioMetrics);
        });
    }

    private void finish(UUID runId, SimulationStatus status, long generated, long processed,
                        List<String> errors, Map<String, Object> scenarioMetrics) {
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
            run.setMetrics(scenarioMetrics);
            repository.save(run);
            progressBroadcaster.broadcastTerminal(runId, runId, status, run.getStartedAt(),
                    generated, processed,
                    metrics != null ? metrics.detections() : 0,
                    metrics != null ? metrics.riskDecisions() : 0,
                    metrics != null ? metrics.alerts() : 0,
                    metrics != null ? metrics.actions() : 0,
                    errors, scenarioMetrics);
        });
        log.info("simulation {} finished status={} generated={} processed={}",
                runId, status, generated, processed);
    }

    /**
     * Builds the per-scenario metrics snapshot stored on the run. Includes the
     * account-takeover attack-chain counters and payment-fraud counters so the
     * dashboard can render each scenario's progression.
     */
    private static Map<String, Object> scenarioMetrics(long failedLogins, long successfulLogins,
                                                        long newIpLogins, long newDeviceLogins,
                                                        long suspiciousPayments,
                                                        long totalPayments, long highValuePayments,
                                                        long declinedPayments, long newDevicePayments,
                                                        long suspiciousIpPayments,
                                                        java.util.Set<String> sourceIps,
                                                        java.util.Set<String> targets,
                                                        java.util.Set<String> fraudCards,
                                                        int attackRate, SimulationType type) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("attackRate", attackRate);
        m.put("failedLogins", failedLogins);
        m.put("successfulLogins", successfulLogins);
        m.put("targetUsers", targets.size());
        m.put("sourceIps", sourceIps.size());
        if (type == SimulationType.ACCOUNT_TAKEOVER) {
            m.put("newIpLogins", newIpLogins);
            m.put("newDeviceLogins", newDeviceLogins);
            m.put("suspiciousPayments", suspiciousPayments);
            m.put("totalTakeoverChains", targets.size());
        }
        if (type == SimulationType.PAYMENT_FRAUD) {
            m.put("totalPayments", totalPayments);
            m.put("highValuePayments", highValuePayments);
            m.put("declinedPayments", declinedPayments);
            m.put("newDevicePayments", newDevicePayments);
            m.put("suspiciousIpPayments", suspiciousIpPayments);
            m.put("fraudRingCards", fraudCards.size());
        }
        return m;
    }

    static double paramDouble(Map<String, Object> params, String key, double fallback) {
        if (params == null) {
            return fallback;
        }
        Object v = params.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

        static long paramLong(Map<String, Object> params, String key, long fallback) {
        if (params == null) {
            return fallback;
        }
        Object v = params.get(key);
        return v instanceof Number n ? n.longValue() : fallback;
    }

    /** Best-effort planned duration (seconds) for progress clamping, honoring scenario overrides. */
    static int progressDuration(Map<String, Object> params) {
        long d = paramLong(params, "botDurationSeconds", 0);
        if (d <= 0) {
            d = paramLong(params, "networkDurationSeconds", 0);
        }
        if (d <= 0) {
            d = paramLong(params, "apiDurationSeconds", 0);
        }
        if (d <= 0) {
            d = paramLong(params, "durationSeconds", 60);
        }
        return (int) Math.max(1, d);
    }

    private void fail(UUID runId, String message) {
        repository.findById(runId).ifPresent(run -> {
            run.setStatus(SimulationStatus.FAILED.name());
            run.setCompletedAt(Instant.now());
                        run.getErrors().add(message);
            repository.save(run);
            progressBroadcaster.broadcastTerminal(runId, runId, SimulationStatus.FAILED,
                    run.getStartedAt(), run.getEventsGenerated(), run.getEventsProcessed(),
                    run.getDetections(), run.getRiskDecisions(), run.getAlerts(), run.getActions(),
                    run.getErrors(), run.getMetrics());
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
