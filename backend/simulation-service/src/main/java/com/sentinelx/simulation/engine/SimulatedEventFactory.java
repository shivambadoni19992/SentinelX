package com.sentinelx.simulation.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
            SimulationType.SUSPICIOUS_OUTBOUND, SimulationType.UNAUTHORIZED_DATA_ACCESS,
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
        return switch (type) {
            case BRUTE_FORCE -> authEvent(runId, sequence, pop, hostile, intensity, true);
            case ACCOUNT_TAKEOVER, SUSPICIOUS_LOGIN, NEW_DEVICE -> authEvent(runId, sequence, pop, hostile, intensity, false);
            case SUSPICIOUS_IP -> apiEvent(runId, sequence, pop, intensity, true, "request");
            case PAYMENT_FRAUD, TRANSACTION_VELOCITY -> paymentEvent(runId, sequence, pop, intensity, false);
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

    private static Map<String, Object> authEvent(UUID runId, long seq, Population pop, boolean hostile,
                                                 int intensity, boolean bruteForce) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Hostile auth traffic concentrates on a small victim population.
        boolean concentrated = bruteForce || rnd.nextDouble() < 0.8;
        String actor = hostile && concentrated
                ? "victim" + rnd.nextInt(0, Math.max(1, intensity / 10 + 1))
                : pop.actor();
        String ip = hostile && rnd.nextDouble() < 0.5 ? Population.rndIp() : pop.ip();
        boolean success = !bruteForce ? rnd.nextDouble() < 0.6 : rnd.nextDouble() < 0.02;
        Map<String, Object> e = base(runId, seq, "LOGIN_ATTEMPT", actor, "login",
                success ? "SUCCESS" : "FAILURE",
                success && hostile ? "HIGH" : (hostile ? "MEDIUM" : "LOW"), ip);
        e.put("authMethod", rnd.nextBoolean() ? "PASSWORD" : "MFA");
        if (hostile) {
            e.put("newDevice", !bruteForce && rnd.nextDouble() < 0.7);
            e.put("unusualGeo", rnd.nextDouble() < 0.6);
        }
        return e;
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

    private static Map<String, Object> apiEvent(UUID runId, long seq, Population pop, int intensity,
                                                boolean hostile, String action) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String actor = hostile ? "bot" + rnd.nextInt(0, 3) : pop.actor();
        boolean denied = hostile ? rnd.nextDouble() < 0.6 : rnd.nextDouble() < 0.05;
        Map<String, Object> e = base(runId, seq, "API_REQUEST", actor, action,
                denied ? "DENIED" : "SUCCESS", denied ? "MEDIUM" : "LOW",
                hostile ? Population.rndIp() : pop.ip());
        e.put("httpStatus", denied ? (rnd.nextBoolean() ? 403 : 429) : 200);
        e.put("latencyMs", rnd.nextInt(5, 50 + Math.max(1, intensity)));
        if (hostile) {
            e.put("userAgent", "Mozilla/5.0 (compatible; SimBot/1.0)");
            e.put("burstSize", 10 + rnd.nextInt(Math.max(1, intensity + 1)));
        }
        return e;
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
