package com.sentinelx.simulation.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.sentinelx.simulation.domain.DockerTopology;
import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationType;

/**
 * Generates realistic security events shaped for the platform's normalizing
 * consumer (security-event-service): every payload carries eventType, action,
 * outcome, severity, sourceIp, occurredAt and a correlationId, and is
 * published onto the same security.* topics the production services emit to.
 * From there the real detection -> risk -> alert pipeline takes over; the
 * simulation never fabricates alerts itself.
 */
public final class SimulatedEventFactory {

    /** Canonical Kafka event topics (mirror of security-event-service's topology). */
    public static final String TOPIC_AUTH = "security.auth";
    public static final String TOPIC_PAYMENT = "security.payment";
    public static final String TOPIC_API = "security.api";
    public static final String TOPIC_RETAIL = "security.retail";
    public static final String TOPIC_NETWORK = "security.network";

    private static final List<SimulationType> ATTACK_TYPES = List.of(
            SimulationType.BRUTE_FORCE, SimulationType.ACCOUNT_TAKEOVER, SimulationType.PAYMENT_FRAUD,
            SimulationType.TRANSACTION_VELOCITY, SimulationType.API_ABUSE, SimulationType.BOT_ACTIVITY,
            SimulationType.SUSPICIOUS_LOGIN, SimulationType.NEW_DEVICE, SimulationType.SUSPICIOUS_IP,
            SimulationType.FAILED_PAYMENTS, SimulationType.CHECKOUT_ABUSE, SimulationType.INVENTORY_SCRAPING,
            SimulationType.COUPON_ABUSE, SimulationType.PORT_SCAN, SimulationType.CONNECTION_SPIKE,
            SimulationType.FAILED_CONNECTIONS, SimulationType.SUSPICIOUS_OUTBOUND,
            SimulationType.UNAUTHORIZED_DATA_ACCESS,
            SimulationType.PRIVILEGED_ACCESS_ANOMALY);

    private SimulatedEventFactory() {
    }

    /** One generated event: the topic it belongs on plus its JSON payload map. */
    public record GeneratedEvent(String topic, Map<String, Object> payload) {
    }

    /**
     * Generates the next event for the run. attackShare (0..1) decides how
     * much of the population is hostile; intensity (0..100) amplifies
     * per-event magnitude (amounts, retry counts, bursts).
     */
    public static GeneratedEvent next(UUID runId, long sequence, SimulationType type,
                                      Population population, double attackShare, int intensity,
                                      Map<String, Object> params) {
        // ACCOUNT_TAKEOVER generates a full attack chain for every event, so the
        // chain steps stay contiguous and the correlation rule can see the whole
        // sequence (failed login → new IP → new device → login → payment).
        if (type == SimulationType.ACCOUNT_TAKEOVER) {
            Map<String, Object> payload = accountTakeoverEvent(runId, sequence, population, intensity, params);
            return new GeneratedEvent(topicFor(eventType(payload), true, type), payload);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean hostile = type.isAttack() && rnd.nextDouble() < attackShare;
        SimulationType effective = !hostile ? SimulationType.NORMAL_TRAFFIC
                : (type == SimulationType.MIXED_ATTACK
                        ? ATTACK_TYPES.get(rnd.nextInt(ATTACK_TYPES.size()))
                        : type);
        Map<String, Object> payload = build(runId, sequence, effective, population, intensity, hostile, params);
        return new GeneratedEvent(topicFor(eventType(payload), hostile, type), payload);
    }

    /** Topic is derived from the concrete event kind so labels match where they land. */
    private static String eventType(Map<String, Object> payload) {
        Object t = payload.get("eventType");
        return t == null ? "" : t.toString();
    }

    public static String topicFor(String eventType, boolean hostile, SimulationType sourceType) {
        String e = eventType == null ? "" : eventType;
        // Authentication events always go to security.auth regardless of hostility,
        // so FailedLoginSpikeRule (scoped to security.auth) can detect brute force.
        if (e.equals("LOGIN_FAILED") || e.equals("LOGIN_SUCCESS") || e.equals("LOGOUT")
                || e.equals("LOGIN_ATTEMPT")) {
            return TOPIC_AUTH;
        }
        if (e.equals("PAYMENT_AUTHORIZED")) {
            return TOPIC_PAYMENT;
        }
        if (e.equals("ORDER_PLACED") || e.equals("RETAIL_ACTIVITY")) {
            return TOPIC_RETAIL;
        }
        if (e.equals("API_REQUEST")) {
            return TOPIC_API;
        }
        if (e.equals("NETWORK_OBSERVATION")) {
            return TOPIC_NETWORK;
        }
        if (hostile) {
            return TOPIC_API;
        }
        return TOPIC_AUTH;
    }

    /** Resolves the population of ids/ips/actors for a run. */
    public static final class Population {
        public final List<UUID> users;
        public final List<UUID> devices;
        public final List<String> ips;
        public final List<String> actors;

        public Population(int users, int devices, int ips) {
            this.users = new ArrayList<>(users);
            for (int i = 0; i < users; i++) {
                this.users.add(seedUuid("user", i));
            }
            this.devices = new ArrayList<>(devices);
            for (int i = 0; i < devices; i++) {
                this.devices.add(seedUuid("device", i));
            }
            this.ips = new ArrayList<>(ips);
            for (int i = 0; i < ips; i++) {
                this.ips.add("10." + (i / 254 % 254) + "." + (i % 254) + "." + ThreadLocalRandom.current().nextInt(2, 250));
            }
            this.actors = new ArrayList<>(users);
            for (int i = 0; i < users; i++) {
                this.actors.add("simuser" + i);
            }
        }

        UUID user() {
            return users.get(ThreadLocalRandom.current().nextInt(users.size()));
        }

        UUID device() {
            return devices.get(ThreadLocalRandom.current().nextInt(devices.size()));
        }

        String ip() {
            return ips.get(ThreadLocalRandom.current().nextInt(ips.size()));
        }

        String actor() {
            return actors.get(ThreadLocalRandom.current().nextInt(actors.size()));
        }

        static String rndIp() {
            return "203.0.113." + ThreadLocalRandom.current().nextInt(2, 250);
        }

        static int rndPort(int min, int max) {
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
    }

    // ---------------------------------------------------------------- build

    private static Map<String, Object> build(UUID runId, long sequence, SimulationType type,
                                             Population pop, int intensity, boolean hostile,
                                             Map<String, Object> params) {
        if (type == SimulationType.PAYMENT_FRAUD) {
            // paymentFraudEvent now returns a GeneratedEvent directly; unwrap the payload.
            return paymentFraudEvent(runId, sequence, pop, intensity, hostile, params).payload();
        }
        if (type == SimulationType.TRANSACTION_VELOCITY) {
            // transactionVelocityEvent returns a GeneratedEvent directly; unwrap the payload.
            return transactionVelocityEvent(runId, sequence, pop, intensity, hostile, params).payload();
        }
        if (type == SimulationType.API_ABUSE) {
            // apiAbuseEvent returns a GeneratedEvent directly; unwrap the payload.
            return apiAbuseEvent(runId, sequence, pop, intensity, hostile, params).payload();
        }
        return switch (type) {
            case BRUTE_FORCE -> authEvent(runId, sequence, pop, hostile, intensity, true, targetUsers(params), attackerIps(params));
            case SUSPICIOUS_LOGIN, NEW_DEVICE -> authEvent(runId, sequence, pop, hostile, intensity, false, targetUsers(params), attackerIps(params));
            case SUSPICIOUS_IP -> apiEvent(runId, sequence, pop, intensity, true, "request");
            case FAILED_PAYMENTS -> paymentEvent(runId, sequence, pop, intensity, true);
            case API_ABUSE -> apiEvent(runId, sequence, pop, intensity, true, "request");
            case BOT_ACTIVITY -> apiEvent(runId, sequence, pop, intensity, true, "page_fetch");
            case UNAUTHORIZED_DATA_ACCESS -> apiEvent(runId, sequence, pop, intensity, true, "data_export");
            case PRIVILEGED_ACCESS_ANOMALY -> apiEvent(runId, sequence, pop, intensity, true, "admin_action");
            case CHECKOUT_ABUSE -> retailEvent(runId, sequence, pop, intensity, "checkout");
            case INVENTORY_SCRAPING -> retailEvent(runId, sequence, pop, intensity, "product_view");
            case COUPON_ABUSE -> retailEvent(runId, sequence, pop, intensity, "coupon_redeem");
            case PORT_SCAN -> networkEvent(runId, sequence, pop, intensity, "port_probe");
            case CONNECTION_SPIKE -> networkEvent(runId, sequence, pop, intensity, "connect");
            case FAILED_CONNECTIONS -> networkEvent(runId, sequence, pop, intensity, "failed_connect");
            case SUSPICIOUS_OUTBOUND -> networkEvent(runId, sequence, pop, intensity, "outbound_transfer");
            default -> benignEvent(runId, sequence, pop, params);
        };
    }

    private static Map<String, Object> base(UUID runId, long sequence, String eventType, String actor,
                                            String action, String outcome, String severity, String sourceIp) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("eventType", eventType);
        e.put("actor", actor);
        e.put("userId", seedUuid("user", hash(actor)).toString());
        e.put("deviceId", seedUuid("device", hash(actor) % 97).toString());
        e.put("sessionId", seedUuid("session", hash(actor) + (int) (sequence % 13)).toString());
        e.put("action", action);
        e.put("outcome", outcome);
        e.put("severity", severity);
        e.put("sourceIp", sourceIp);
        e.put("occurredAt", Instant.now().toString());
        // Attribution: lets the downstream tracker count what the pipeline
        // produced for THIS simulation without the simulation touching alerts.
        e.put("correlationId", correlationId(runId, sequence));
        e.put("simulationId", runId.toString());
        return e;
    }

    public static String correlationId(UUID runId, long sequence) {
        return "sim-" + runId + "-" + sequence;
    }

    /**
     * Authentication-event generator. Real failures are labelled LOGIN_FAILED so
     * the platform's FailedLoginSpikeRule (which matches that event type) can
     * detect brute-force. Brute force concentrates all attempts on a small set
     * of target users, producing actual failed-login events for each.
     */
    private static Map<String, Object> authEvent(UUID runId, long seq, Population pop, boolean hostile,
                                                 int intensity, boolean bruteForce, int targetUsers, int attackerIps) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Hostile auth traffic concentrates on a small victim population.
        boolean concentrated = bruteForce || rnd.nextDouble() < 0.8;
        String actor = hostile && concentrated
                ? "victim" + rnd.nextInt(0, Math.max(1, targetUsers))
                : pop.actor();
        String ip;
        if (hostile && attackerIps > 0) {
            ip = sourceIp(attackerIps, rnd); // dedicated attacker IP pool
        } else if (hostile && rnd.nextDouble() < 0.5) {
            ip = Population.rndIp();
        } else {
            ip = pop.ip();
        }
        boolean success = bruteForce ? rnd.nextDouble() < 0.02 : rnd.nextDouble() < 0.6;
        // Actual failed authentication events — labelled for the detection engine.
        String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
        Map<String, Object> e = base(runId, seq, eventType, actor, "login",
                success ? "SUCCESS" : "FAILURE",
                success && hostile ? "HIGH" : (hostile ? "MEDIUM" : "LOW"), ip);
        e.put("authMethod", "PASSWORD");
        if (hostile) {
            e.put("newDevice", !bruteForce && rnd.nextDouble() < 0.7);
            e.put("unusualGeo", rnd.nextDouble() < 0.6);
        }
        return e;
    }

    /**
     * Generates the full account-takeover attack chain for one event. Each victim
     * walks through a fixed sequence: failed logins → login from a new IP → login
     * from a new device → successful login → suspicious payment. The sequence
     * number drives which victim and which phase we are at, so the chain stays
     * contiguous and the ACCOUNT_TAKEOVER_SUSPECTED rule can see the whole
     * progression.
     */
    private static Map<String, Object> accountTakeoverEvent(UUID runId, long seq, Population pop,
                                                            int intensity, Map<String, Object> params) {
        int targetUsers = targetUsers(params);
        int failedAttempts = (int) num(params, "failedAttemptsPerUser", 10);
        int attackerIpPool = attackerIps(params);
        double newDevicePct = num(params, "newDevicePercentage", 80) / 100.0;
        double newIpPct = num(params, "newIpPercentage", 80) / 100.0;
        double successLoginPct = num(params, "successfulLoginPercentage", 90) / 100.0;
        double highValuePct = num(params, "highValueTransactionPercentage", 70) / 100.0;
        double txnAmount = num(params, "transactionAmount", 5000);

        int chainLength = failedAttempts + 4;
        long globalChain = seq / chainLength;
        int step = (int) (seq % chainLength);
        int victimIdx = (int) (globalChain % targetUsers);
        String victim = "victim" + victimIdx;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String normalDevice = seedUuid("device", hash(victim) % 97).toString();

        // Phase 1: failed login attempts from attacker IPs with the victim's normal device.
        if (step < failedAttempts) {
            String ip = sourceIp(attackerIpPool, rnd);
            Map<String, Object> e = base(runId, seq, "LOGIN_FAILED", victim, "login", "FAILURE", "MEDIUM", ip);
            e.put("authMethod", "PASSWORD");
            e.put("deviceId", normalDevice);
            return e;
        }

        // Phase 2: login from a new IP (attacker now has credentials).
        if (step == failedAttempts) {
            boolean useNewIp = rnd.nextDouble() < newIpPct;
            String ip = useNewIp ? "198.51.100." + rnd.nextInt(2, 250) : sourceIp(attackerIpPool, rnd);
            boolean success = rnd.nextDouble() < successLoginPct;
            String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
            Map<String, Object> e = base(runId, seq, eventType, victim, "login",
                    success ? "SUCCESS" : "FAILURE", "HIGH", ip);
            e.put("authMethod", "PASSWORD");
            e.put("deviceId", normalDevice);
            return e;
        }

        // Phase 3: login from a new device.
        if (step == failedAttempts + 1) {
            boolean useNewDevice = rnd.nextDouble() < newDevicePct;
            String ip = sourceIp(attackerIpPool, rnd);
            String deviceId = useNewDevice ? seedUuid("device", hash(victim) % 97 + 1000).toString() : normalDevice;
            boolean success = rnd.nextDouble() < successLoginPct;
            String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
            Map<String, Object> e = base(runId, seq, eventType, victim, "login",
                    success ? "SUCCESS" : "FAILURE", "HIGH", ip);
            e.put("authMethod", "PASSWORD");
            e.put("deviceId", deviceId);
            return e;
        }

        // Phase 4: successful login — attacker is fully inside the account.
        if (step == failedAttempts + 2) {
            String newIp = "198.51.100." + rnd.nextInt(2, 250);
            String newDevice = seedUuid("device", hash(victim) % 97 + 1000).toString();
            Map<String, Object> e = base(runId, seq, "LOGIN_SUCCESS", victim, "login", "SUCCESS", "HIGH", newIp);
            e.put("authMethod", "PASSWORD");
            e.put("deviceId", newDevice);
            return e;
        }

        // Phase 5: suspicious payment — high-value transaction by the attacker.
        boolean useHighValue = rnd.nextDouble() < highValuePct;
        double amount = useHighValue ? txnAmount : Math.round((50 + rnd.nextDouble() * 100) * 100.0) / 100.0;
        String ip = sourceIp(attackerIpPool, rnd);
        Map<String, Object> e = base(runId, seq, "PAYMENT_AUTHORIZED", victim, "authorize", "APPROVED",
                useHighValue ? "HIGH" : "MEDIUM", ip);
        e.put("amount", amount);
        e.put("currency", "USD");
        e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
        return e;
    }

    /** Number of target accounts a brute-force run focuses on. */
    private static int targetUsers(Map<String, Object> params) {
        double t = num(params, "targetUsers", 5);
        return Math.max(1, (int) Math.min(t, 10_000));
    }

    /** Attacker source-IP pool size (authSourceIps, falling back to sourceIps). */
    private static int attackerIps(Map<String, Object> params) {
        double a = num(params, "authSourceIps", 0);
        if (a <= 0) {
            a = num(params, "sourceIps", 0);
        }
        return (int) Math.max(0, Math.min(a, 10_000));
    }

    /** Picks one IP from a bounded attacker pool of the configured size. */
    private static String sourceIp(int poolSize, ThreadLocalRandom rnd) {
        int size = Math.max(1, Math.min(poolSize, 250));
        return "203.0.113." + (2 + rnd.nextInt(size));
    }

    private static Map<String, Object> paymentEvent(UUID runId, long seq, Population pop,
                                                    int intensity, boolean declines) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String actor = "carduser" + rnd.nextInt(0, Math.max(1, intensity / 10 + 1));
        boolean declined = declines ? rnd.nextDouble() < 0.85 : rnd.nextDouble() < 0.3;
        Map<String, Object> e = base(runId, seq, "PAYMENT_AUTHORIZED", actor, "authorize",
                declined ? "DECLINED" : "APPROVED", declined ? "HIGH" : "MEDIUM", pop.ip());
        e.put("amount", Math.round((50 + rnd.nextDouble() * 50 * Math.max(1, intensity)) * 100.0) / 100.0);
        e.put("currency", "USD");
        e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
        return e;
    }

    /**
     * Generates a payment event shaped for the PAYMENT_FRAUD scenario. Uses all
     * the scenario knobs to produce realistic fraud patterns:
     * <ul>
     *   <li>normalAmount / suspiciousAmount / highValueAmount — tiered amounts</li>
     *   <li>failedPaymentPercentage — chance of declined payment</li>
     *   <li>newDevicePercentage — payment from an unrecognized device</li>
     *   <li>suspiciousIpPercentage — payment from a bad-reputation IP</li>
     *   <li>velocity — number of cards/users in the fraud ring</li>
     * </ul>
     * Hostile traffic concentrates on a small set of fraud-ring cards; benign
     * traffic uses normal amounts from the population's regular devices and IPs.
     */
    public static GeneratedEvent paymentFraudEvent(UUID runId, long seq, Population pop,
                                                  int intensity, boolean hostile,
                                                  Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double normalAmount = num(params, "normalAmount", 80);
        double suspiciousAmount = num(params, "suspiciousAmount", 1_200);
        double highValueAmount = num(params, "highValueAmount", 9_500);
        double failedPct = num(params, "failedPaymentPercentage", 35) / 100.0;
        double newDevicePct = num(params, "newDevicePercentage", 30) / 100.0;
        double suspiciousIpPct = num(params, "suspiciousIpPercentage", 40) / 100.0;
        int velocity = (int) Math.max(1, num(params, "velocity", 10));

        Map<String, Object> e;
        if (!hostile) {
            // Benign payment: normal amount, normal device, normal IP.
            String actor = "carduser" + rnd.nextInt(0, Math.max(1, pop.actors.size() / 3));
            boolean declined = rnd.nextDouble() < 0.02;
            double amount = Math.max(1, Math.round(normalAmount * (0.5 + rnd.nextDouble()) * 100.0) / 100.0);
            e = base(runId, seq, "PAYMENT_CREATED", actor, "authorize",
                    declined ? "DECLINED" : "APPROVED", declined ? "MEDIUM" : "LOW", pop.ip());
            e.put("amount", amount);
            e.put("currency", "USD");
            e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
            return new GeneratedEvent(TOPIC_PAYMENT, e);
        }

        // Hostile payment: fraud ring uses few cards at high velocity with
        // suspicious attributes. Amount is drawn from the configured tiers.
        String actor = "fraudcard" + rnd.nextInt(0, velocity);
        double roll = rnd.nextDouble();
        double amount;
        if (roll < 0.15) {
            amount = Math.round(highValueAmount * (0.8 + rnd.nextDouble() * 0.4) * 100.0) / 100.0;
        } else if (roll < 0.55) {
            amount = Math.round(suspiciousAmount * (0.7 + rnd.nextDouble() * 0.6) * 100.0) / 100.0;
        } else {
            amount = Math.round((5 + rnd.nextDouble() * 50) * 100.0) / 100.0;
        }

        boolean declined = rnd.nextDouble() < failedPct;
        String severity = (amount >= highValueAmount * 0.8) ? "HIGH" : (declined ? "HIGH" : "MEDIUM");
        String ip;
        boolean suspiciousIp;
        if (rnd.nextDouble() < suspiciousIpPct) {
            ip = "198.51.100." + rnd.nextInt(2, 250);
            suspiciousIp = true;
        } else {
            ip = Population.rndIp();
            suspiciousIp = false;
        }

        String deviceId;
        boolean newDevice;
        if (rnd.nextDouble() < newDevicePct) {
            deviceId = seedUuid("device", 50_000 + rnd.nextInt(0, 1000)).toString();
            newDevice = true;
        } else {
            deviceId = seedUuid("device", hash(actor) % 97).toString();
            newDevice = false;
        }

        e = base(runId, seq, "PAYMENT_CREATED", actor, "authorize",
                declined ? "DECLINED" : "APPROVED", severity, ip);
        e.put("amount", amount);
        e.put("currency", "USD");
        e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
        e.put("deviceId", deviceId);
        e.put("newDevice", newDevice);
        e.put("suspiciousIp", suspiciousIp);
        return new GeneratedEvent(TOPIC_PAYMENT, e);
    }

    /**
     * Generates rapid payment activity for the TRANSACTION_VELOCITY scenario.
     *
     * <p>Hostile payments are burst-fired from a small set of concurrent users
     * (the fraud ring) at a rate far exceeding normal human behaviour. Each user
     * fires {@code transactionsPerUser} payments within the configured time window,
     * triggering the TransactionVelocityRule (5+ payments in 60 seconds).
     *
     * <p>Benign payments are spread across the full user population at a normal pace.
     *
     * @param runId     simulation run id
     * @param seq       sequence number for deterministic correlation
     * @param pop       population of users, devices and IPs
     * @param intensity 0..100 — amplifies the burst rate and amount
     * @param hostile   whether this event should be hostile (part of the attack)
     * @param params    scenario parameters: users, transactionsPerUser, timeWindowSeconds,
     *                  amount, concurrentUsers, attackPercentage
     * @return a GeneratedEvent routed to the payment topic
     */
    public static GeneratedEvent transactionVelocityEvent(UUID runId, long seq, Population pop,
                                                          int intensity, boolean hostile,
                                                          Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double amount = num(params, "amount", 500);
        int concurrentUsers = Math.max(1, (int) num(params, "concurrentUsers", 5));
        int txnPerUser = Math.max(1, (int) num(params, "transactionsPerUser", 20));
        int timeWindowSeconds = Math.max(1, (int) num(params, "timeWindowSeconds", 60));

        Map<String, Object> e;
        if (!hostile) {
            // Benign payment: normal user, normal pace, normal amount.
            String actor = "user" + rnd.nextInt(0, Math.max(1, pop.actors.size()));
            boolean declined = rnd.nextDouble() < 0.02;
            double amt = Math.max(1, Math.round(amount * (0.3 + rnd.nextDouble() * 0.7) * 100.0) / 100.0);
            e = base(runId, seq, "PAYMENT_CREATED", actor, "authorize",
                    declined ? "DECLINED" : "APPROVED", declined ? "MEDIUM" : "LOW", pop.ip());
            e.put("amount", amt);
            e.put("currency", "USD");
            e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
            e.put("deviceId", seedUuid("device", hash(actor) % 97).toString());
            return new GeneratedEvent(TOPIC_PAYMENT, e);
        }

        // Hostile payment: burst-fired from a small concurrent-user fraud ring.
        // The actor is selected from the concurrent-user pool, and the amount
        // is scaled up with intensity to trigger velocity + amount rules.
        int userIdx = (int) (seq % concurrentUsers);
        String actor = "burstuser" + userIdx;
        double burstAmount = Math.max(1, Math.round(amount * (0.5 + rnd.nextDouble() * (1.0 + intensity / 50.0)) * 100.0) / 100.0);
        boolean declined = rnd.nextDouble() < 0.15; // higher decline rate under burst
        String severity = burstAmount >= 5000 ? "HIGH" : (declined ? "MEDIUM" : "LOW");
        String ip = Population.rndIp();
        String deviceId = seedUuid("device", 50_000 + rnd.nextInt(0, 200)).toString();

        e = base(runId, seq, "PAYMENT_CREATED", actor, "authorize",
                declined ? "DECLINED" : "APPROVED", severity, ip);
        e.put("amount", burstAmount);
        e.put("currency", "USD");
        e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
        e.put("deviceId", deviceId);
        e.put("newDevice", rnd.nextDouble() < 0.4); // 40% new device
        e.put("suspiciousIp", rnd.nextDouble() < 0.3); // 30% suspicious IP
        return new GeneratedEvent(TOPIC_PAYMENT, e);
    }

    private static Map<String, Object> apiEvent(UUID runId, long seq, Population pop, int intensity,
                                                boolean hostile, String action) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String actor = hostile ? "bot" + rnd.nextInt(0, 3) : pop.actor();
        boolean denied = hostile ? rnd.nextDouble() < 0.6 : rnd.nextDouble() < 0.05;
        Map<String, Object> e = base(runId, seq, "API_REQUEST", actor, action,
                denied ? "DENIED" : "SUCCESS", denied ? "MEDIUM" : "LOW",
                hostile ? Population.rndIp() : pop.ip());
        e.put("path", "/api/" + (hostile ? "payments" : "products"));
        e.put("httpStatus", denied ? (rnd.nextBoolean() ? 403 : 429) : 200);
        e.put("latencyMs", rnd.nextInt(5, 50 + Math.max(1, intensity)));
        if (hostile) {
            e.put("userAgent", "Mozilla/5.0 (compatible; SimBot/1.0)");
            e.put("burstSize", 10 + rnd.nextInt(Math.max(1, intensity + 1)));
        }
        return e;
    }

    /**
     * Generates an API-abuse event: a flood of requests from a small set of
     * attacker IPs hitting a single target endpoint at a high rate. The path
     * is taken from the {@code targetEndpoint} param (default /api/payments).
     *
     * @return a GeneratedEvent routed to the API topic
     */
    public static GeneratedEvent apiAbuseEvent(UUID runId, long seq, Population pop,
                                               int intensity, boolean hostile,
                                               java.util.Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String targetEndpoint = params != null ? String.valueOf(params.getOrDefault("targetEndpoint", "/api/payments")) : "/api/payments";
        int sourceIps = params != null && params.get("apiSourceIps") instanceof Number n ? n.intValue() : 15;
        String actor = hostile ? "abuser" + rnd.nextInt(0, Math.max(1, sourceIps)) : pop.actor();
        String ip = hostile ? "198.51.100." + rnd.nextInt(1, Math.max(2, sourceIps + 1)) : pop.ip();
        boolean denied = hostile ? rnd.nextDouble() < 0.7 : rnd.nextDouble() < 0.03;
        Map<String, Object> e = base(runId, seq, "API_REQUEST", actor, "request",
                denied ? "DENIED" : "SUCCESS", denied ? "HIGH" : "LOW", ip);
        e.put("path", targetEndpoint);
        e.put("httpStatus", denied ? (rnd.nextBoolean() ? 403 : 429) : 200);
        e.put("latencyMs", denied ? rnd.nextInt(10, 200) : rnd.nextInt(5, 40));
        if (hostile) {
            e.put("userAgent", "Mozilla/5.0 (compatible; SimBot/1.0)");
            e.put("burstSize", 50 + rnd.nextInt(Math.max(1, intensity * 2)));
        }
        return new GeneratedEvent(TOPIC_API, e);
    }

    /**
     * Generates a bot-activity event: requests from a bot fleet (dedicated
     * source IPs + the configured bot user-agent) hitting one of the target
     * endpoints, or a benign request from a legitimate user with a browser
     * user-agent. The generator is invoked directly by the dedicated
     * {@code botActivity} runner which manages the bot/legitimate mix.
     *
     * @return a GeneratedEvent routed to the API topic
     */
    public static GeneratedEvent botActivityEvent(UUID runId, long seq, Population pop,
                                                  int intensity, boolean bot,
                                                  java.util.Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int botCount = params != null && params.get("botCount") instanceof Number n ? n.intValue() : 50;
        String userAgentPattern = params != null
                ? String.valueOf(params.getOrDefault("botUserAgentPattern", "SimBot/1.0"))
                : "SimBot/1.0";
        String endpoint = pickEndpoint(params);
        String actor = bot ? "botclient" + rnd.nextInt(0, Math.max(1, botCount)) : pop.actor();
        String ip = bot ? "203.0.113." + rnd.nextInt(2, 250) : pop.ip();
        String userAgent = bot
                ? userAgentPattern.replace("$VERSION", String.valueOf(1 + rnd.nextInt(4)))
                : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/126.0";
        Map<String, Object> e = base(runId, seq, "API_REQUEST", actor, "page_fetch",
                "SUCCESS", bot ? "MEDIUM" : "LOW", ip);
        e.put("path", endpoint);
        e.put("userAgent", userAgent);
        e.put("httpStatus", 200);
        e.put("latencyMs", bot ? rnd.nextInt(3, 35) : rnd.nextInt(20, 140));
        if (bot) {
            e.put("burstSize", 5 + rnd.nextInt(Math.max(1, intensity + 1)));
            e.put("botRequest", true);
        }
        return new GeneratedEvent(TOPIC_API, e);
    }

    /** Picks a target endpoint from the comma-separated {@code targetEndpoints} param. */
    private static String pickEndpoint(java.util.Map<String, Object> params) {
        String raw = params != null
                ? String.valueOf(params.getOrDefault("targetEndpoints", "/api/products, /api/search"))
                : "/api/products, /api/search";
        String[] parts = raw.split(",");
        if (parts.length == 0) {
            return "/api/products";
        }
        return parts[ThreadLocalRandom.current().nextInt(parts.length)].trim();
    }

    private static Map<String, Object> retailEvent(UUID runId, long seq, Population pop,
                                                   int intensity, String action) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean abusive = rnd.nextDouble() < 0.7;
        Map<String, Object> e = base(runId, seq, "RETAIL_ACTIVITY",
                abusive ? "scraper" + rnd.nextInt(0, 3) : pop.actor(),
                action, "SUCCESS", abusive ? "MEDIUM" : "LOW", abusive ? Population.rndIp() : pop.ip());
        e.put("sku", "SKU-" + rnd.nextInt(10_000, 99_999));
        e.put("quantity", action.equals("checkout") ? 1 + rnd.nextInt(1 + Math.max(1, intensity / 10)) : 1);
        if (action.equals("coupon_redeem")) {
            e.put("couponCode", "SAVE" + rnd.nextInt(10, 50));
            e.put("outcome", abusive && rnd.nextDouble() < 0.7 ? "REJECTED" : "SUCCESS");
        }
        return e;
    }

    private static Map<String, Object> networkEvent(UUID runId, long seq, Population pop,
                                                    int intensity, String action) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<String, Object> e = base(runId, seq, "NETWORK_OBSERVATION", "sensor-" + rnd.nextInt(1, 5), action,
                action.equals("port_probe") ? "DENIED" : "SUCCESS", "HIGH", Population.rndIp());
        e.put("destinationIp", "10.0." + rnd.nextInt(0, 255) + "." + rnd.nextInt(2, 250));
        e.put("destinationPort", action.equals("port_probe") ? Population.rndPort(1, 9000) : 443);
        if (action.equals("outbound_transfer")) {
            e.put("bytesTransferred", 1_000_000L * (1 + rnd.nextInt(Math.max(1, intensity))));
        } else if (action.equals("connect")) {
            e.put("connectionCount", 100 + rnd.nextInt(100 * Math.max(1, intensity / 10 + 1)));
        } else {
            e.put("attemptCount", 5 + rnd.nextInt(Math.max(1, intensity)));
        }
        return e;
    }

    /**
     * Generates a synthetic network-observation event for the NETWORK_* drills.
     * Safety guarantees (enforced, not advisory):
     *
     * <ul>
     *   <li>Source and destination are always Docker Compose containers
     *       resolved through {@link DockerTopology} — unknown or external
     *       targets are rejected before any event is built.</li>
     *   <li>Destination IPs are compose-internal {@code 10.0.x.x}; source IPs
     *       use the documentation-only TEST-NET-3 range. Nothing routable.</li>
     *   <li>The event is data only — it is published to Kafka, never dialled.</li>
     * </ul>
     *
     * @return a GeneratedEvent routed to the network topic
     */
    public static GeneratedEvent networkObservationEvent(UUID runId, long seq, Population pop,
                                                         SimulationType type, int intensity,
                                                         Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String action = switch (type) {
            case PORT_SCAN -> "port_probe";
            case CONNECTION_SPIKE -> "connect";
            case FAILED_CONNECTIONS -> "failed_connect";
            case SUSPICIOUS_OUTBOUND -> "outbound_transfer";
            default -> "connect";
        };
        boolean denied = "port_probe".equals(action) || "failed_connect".equals(action);

        DockerTopology.Container source = resolveContainerParam(params, "sourceContainers", "api-gateway");
        DockerTopology.Container target = resolveContainerParam(params, "targetContainers", null);

        int port = pickPort(params, target, type);
        String protocol = rnd.nextDouble() < 0.85 ? "TCP" : "UDP";

        Map<String, Object> e = base(runId, seq, "NETWORK_OBSERVATION", source.containerName(), action,
                denied ? "DENIED" : "SUCCESS", denied ? "MEDIUM" : "LOW", source.internalIp());
        e.put("sourceContainer", source.containerName());
        e.put("destinationContainer", target.containerName());
        e.put("destinationIp", target.internalIp());
        e.put("destinationPort", port);
        e.put("protocol", protocol);
        e.put("internal", true);
        e.put("direction", "outbound_transfer".equals(action) ? "outbound" : "internal");
        switch (action) {
            case "port_probe", "failed_connect" -> e.put("attemptCount", 1 + rnd.nextInt(Math.max(1, intensity)));
            case "connect" -> e.put("connectionCount", 1 + rnd.nextInt(Math.max(1, intensity)));
            case "outbound_transfer" -> {
                // Transfer size scales with intensity; large transfers (>= 1 GiB)
                // trip SuspiciousOutboundRule. Destination stays internal.
                long mb = 100L * (1 + rnd.nextInt(Math.max(1, intensity)));
                e.put("bytesTransferred", mb * 1_000_000L);
            }
            default -> { }
        }
        return new GeneratedEvent(TOPIC_NETWORK, e);
    }

    /**
     * Benign baseline traffic: a realistic mix of logins, API requests,
     * payments, orders and logouts. The payment share is driven by the
     * scenario's transactionsPerSecond knob (relative to eventsPerSecond) and
     * payment amounts by normalAmount, both supplied via the run configuration.
     */
    private static Map<String, Object> benignEvent(UUID runId, long seq, Population pop,
                                                   Map<String, Object> params) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double eps = Math.max(1, num(params, "eventsPerSecond", 10));
        double txnPerSec = num(params, "transactionsPerSecond", eps * 0.2);
        double normalAmount = num(params, "normalAmount", 80);
        // Payments cannot dominate the mix even if the knob asks for it.
        double paymentShare = Math.max(0.05, Math.min(0.5, txnPerSec / eps));
        double rest = (1.0 - paymentShare) / 4.0;
        double r = rnd.nextDouble();
        if (r < rest) {
            return loginEvent(runId, seq, pop, false);
        } else if (r < rest * 2) {
            return apiEvent(runId, seq, pop, 5, false, "request");
        } else if (r < rest * 2 + paymentShare) {
            return benignPaymentEvent(runId, seq, pop, normalAmount);
        } else if (r < rest * 3 + paymentShare) {
            return orderEvent(runId, seq, pop, normalAmount);
        }
        return logoutEvent(runId, seq, pop);
    }

    private static double num(Map<String, Object> params, String key, double fallback) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    /** Resolves a container-list param against the compose topology; never external. */
    private static DockerTopology.Container resolveContainerParam(Map<String, Object> params,
                                                                  String key, String fallback) {
        String raw = params != null ? String.valueOf(params.get(key)) : null;
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            if (fallback == null) {
                throw new IllegalArgumentException(key + " is required for network simulations");
            }
            raw = fallback;
        }
        String[] names = raw.split(",");
        String pick = names[ThreadLocalRandom.current().nextInt(names.length)].trim();
        return DockerTopology.resolve(pick);
    }

    /**
     * Picks the destination port: explicit comma-separated {@code ports} win,
     * then the target container's own service ports; a numeric {@code ports}
     * count drives the port-scan sweep range (1..N).
     */
    private static int pickPort(Map<String, Object> params, DockerTopology.Container target,
                                SimulationType type) {
        Object raw = params != null ? params.get("ports") : null;
        if (raw instanceof String s && !s.isBlank() && !"null".equals(s)) {
            String[] parts = s.split(",");
            try {
                return Integer.parseInt(parts[ThreadLocalRandom.current().nextInt(parts.length)].trim());
            } catch (NumberFormatException ignored) {
                // fall through to container ports
            }
        }
        if (raw instanceof Number n && type == SimulationType.PORT_SCAN) {
            return 1 + ThreadLocalRandom.current().nextInt(Math.max(1, n.intValue()));
        }
        List<Integer> ports = target.ports();
        if (ports.isEmpty()) {
            return 443;
        }
        return ports.get(ThreadLocalRandom.current().nextInt(ports.size()));
    }

    private static Map<String, Object> loginEvent(UUID runId, long seq, Population pop, boolean logout) {
        Map<String, Object> e = base(runId, seq, logout ? "LOGOUT" : "LOGIN_ATTEMPT", pop.actor(),
                logout ? "logout" : "login", "SUCCESS", "LOW", pop.ip());
        e.put("authMethod", "PASSWORD");
        return e;
    }

    private static Map<String, Object> logoutEvent(UUID runId, long seq, Population pop) {
        return loginEvent(runId, seq, pop, true);
    }

    private static Map<String, Object> benignPaymentEvent(UUID runId, long seq, Population pop,
                                                          double normalAmount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double amount = Math.max(1,
                Math.round(normalAmount * (0.5 + rnd.nextDouble()) * 100.0) / 100.0);
        boolean declined = rnd.nextDouble() < 0.02; // benign decline rate
        Map<String, Object> e = base(runId, seq, "PAYMENT_AUTHORIZED", pop.actor(), "authorize",
                declined ? "DECLINED" : "APPROVED", declined ? "MEDIUM" : "LOW", pop.ip());
        e.put("amount", amount);
        e.put("currency", "USD");
        e.put("cardLast4", String.format("%04d", rnd.nextInt(0, 10_000)));
        return e;
    }

    private static Map<String, Object> orderEvent(UUID runId, long seq, Population pop, double normalAmount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Map<String, Object> e = base(runId, seq, "ORDER_PLACED", pop.actor(), "order_placed",
                "SUCCESS", "LOW", pop.ip());
        e.put("orderId", "ORD-" + String.format("%06d", rnd.nextInt(0, 1_000_000)));
        e.put("amount", Math.max(1, Math.round(normalAmount * (0.4 + rnd.nextDouble() * 1.6) * 100.0) / 100.0));
        e.put("currency", "USD");
        e.put("itemCount", 1 + rnd.nextInt(4));
        return e;
    }

    private static UUID seedUuid(String namespace, int i) {
        return UUID.nameUUIDFromBytes((namespace + ":" + i).getBytes());
    }

    private static int hash(String s) {
        return Math.floorMod(s.hashCode(), 100_000);
    }
}
