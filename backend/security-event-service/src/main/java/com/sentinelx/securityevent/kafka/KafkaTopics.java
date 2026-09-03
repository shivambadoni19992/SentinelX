package com.sentinelx.securityevent.kafka;

import java.util.List;

/**
 * Canonical SentinelX Kafka topology. Every producer in the platform publishes
 * JSON events to one of these topics; {@code security-event-service} is the
 * single normalizing consumer. Each topic has a paired {@code *.dlt}
 * dead-letter topic that receives records after retry exhaustion.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String AUTH = "security.auth";
    public static final String PAYMENT = "security.payment";
    public static final String API = "security.api";
    public static final String RETAIL = "security.retail";
    public static final String NETWORK = "security.network";
    public static final String RISK = "security.risk";
    public static final String ALERT = "security.alert";
    public static final String AUDIT = "security.audit";

    /** All normalizable event topics, in stable order. */
    public static final List<String> ALL = List.of(
            AUTH, PAYMENT, API, RETAIL, NETWORK, RISK, ALERT, AUDIT);

    /** Dead-letter topic paired with the given event topic. */
    public static String dltFor(String topic) {
        return topic + ".dlt";
    }

    /** The source event topic for a dead-letter topic name, or null. */
    public static String sourceFor(String dltTopic) {
        return dltTopic.endsWith(".dlt") ? dltTopic.substring(0, dltTopic.length() - 4) : null;
    }
}
