package com.sentinelx.risk.model;

import java.util.Set;

/**
 * The ten risk signals the engine scores. Each signal carries a weight
 * (points added per contributing detection, capped at {@code cap}), the
 * detection rule ids that raise it, and a human-readable label so every
 * computed score can be explained line by line.
 */
public enum RiskSignal {

    FAILED_LOGINS("failed logins", 20, Set.of("FAILED_LOGIN_SPIKE")),
    NEW_DEVICE("new device", 15, Set.of("NEW_DEVICE")),
    NEW_IP("new IP", 10, Set.of("NEW_IP")),
    TRANSACTION_AMOUNT("transaction amount", 25, Set.of("UNUSUAL_TRANSACTION_AMOUNT")),
    TRANSACTION_VELOCITY("transaction velocity", 20, Set.of("TRANSACTION_VELOCITY")),
    FAILED_PAYMENTS("failed payments", 20, Set.of("MULTIPLE_FAILED_PAYMENTS")),
    API_RATE("API rate", 15, Set.of("API_REQUEST_SPIKE")),
    BOT_ACTIVITY("bot activity", 20, Set.of("BOT_ACTIVITY")),
    NETWORK_ANOMALY("network anomaly", 25, Set.of("PORT_SCAN", "CONNECTION_SPIKE", "SUSPICIOUS_OUTBOUND")),
    DATA_ACCESS_ANOMALY("data access anomaly", 30, Set.of("UNAUTHORIZED_DATA_ACCESS", "PRIVILEGED_ACCESS_ANOMALY"));

    /** How many times one signal may stack before it stops adding points. */
    public static final int MAX_STACK = 2;

    private final String label;
    private final int weight;
    private final Set<String> ruleIds;

    RiskSignal(String label, int weight, Set<String> ruleIds) {
        this.label = label;
        this.weight = weight;
        this.ruleIds = ruleIds;
    }

    public String label() {
        return label;
    }

    public int weight() {
        return weight;
    }

    public Set<String> ruleIds() {
        return ruleIds;
    }

    /** Points this signal contributes after {@code hits} occurrences. */
    public int points(int hits) {
        return Math.min(hits, MAX_STACK) * weight;
    }

    /** Resolves the signal raised by a detection rule id, or null. */
    public static RiskSignal forRuleId(String ruleId) {
        if (ruleId == null) {
            return null;
        }
        for (RiskSignal s : values()) {
            if (s.ruleIds.contains(ruleId)) {
                return s;
            }
        }
        return null;
    }
}