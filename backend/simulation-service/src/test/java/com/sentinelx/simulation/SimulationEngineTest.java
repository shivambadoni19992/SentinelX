package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationLimits;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.engine.SimulatedEventFactory;
import com.sentinelx.simulation.engine.SimulatedEventFactory.Population;

class SimulationEngineTest {

    private final SimulationLimits.Limits limits = new SimulationLimits.Limits(
            10_000, 10_000, 10_000, 600, 1_000, 50_000);

    @Test
    void defaultsFallBackWhenFieldsAreNull() {
        SimulationConfig config = new SimulationConfig(null, null, null, null, null, null, null);
        assertThat(config.numberOfUsers()).isEqualTo(100);
        assertThat(config.numberOfIpAddresses()).isEqualTo(50);
        assertThat(config.intensity()).isEqualTo(50);
        config.validate(limits);
    }

    @Test
    void overLimitConfigsAreRejected() {
        SimulationConfig tooLong = new SimulationConfig(10, 10, 10, 601, 10, 10, 10);
        assertThatThrownBy(() -> tooLong.validate(limits)).hasMessageContaining("durationSeconds");

        SimulationConfig tooFast = new SimulationConfig(10, 10, 10, 60, 1_001, 10, 10);
        assertThatThrownBy(() -> tooFast.validate(limits)).hasMessageContaining("eventsPerSecond");

        SimulationConfig tooBigTotal = new SimulationConfig(10, 10, 10, 600, 1_000, 10, 10);
        assertThatThrownBy(() -> tooBigTotal.validate(limits)).hasMessageContaining("total events");

        SimulationConfig badShare = new SimulationConfig(10, 10, 10, 60, 10, 101, 10);
        assertThatThrownBy(() -> badShare.validate(limits)).hasMessageContaining("attackPercentage");

        SimulationConfig badIntensity = new SimulationConfig(10, 10, 10, 60, 10, 10, -1);
        assertThatThrownBy(() -> badIntensity.validate(limits)).hasMessageContaining("intensity");
    }

    @Test
    void generatedEventsCarryPipelineShapedPayloads() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(10, 5, 4);
        SimulationConfig config = SimulationConfig.defaults();

        for (SimulationType type : SimulationType.values()) {
            SimulatedEventFactory.GeneratedEvent event =
                    SimulatedEventFactory.next(runId, 1, type, population, 1.0, 50, Map.of());
            Map<String, Object> payload = event.payload();
            assertThat(event.topic()).startsWith("security.");
            assertThat(payload).containsKeys("eventType", "action", "outcome", "severity",
                    "sourceIp", "occurredAt", "correlationId", "simulationId");
            assertThat(payload.get("correlationId")).isEqualTo("sim-" + runId + "-1");
            assertThat(payload.get("simulationId")).isEqualTo(runId.toString());
        }
    }

    @Test
    void bruteForceProducesMostlyFailedLoginsOnFewAccounts() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 20, 10);
        int failures = 0;
        Set<Object> actors = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            var payload = SimulatedEventFactory.next(runId, i, SimulationType.BRUTE_FORCE,
                    population, 1.0, 50, Map.of()).payload();
            if ("FAILURE".equals(payload.get("outcome"))) {
                failures++;
            }
            actors.add(payload.get("actor"));
        }
        assertThat(failures).isGreaterThan(400); // ~98% failures
        assertThat(actors.size()).isLessThanOrEqualTo(10); // concentrated on few victims
    }

    @Test
    void mixedAttackSpreadsAcrossVectors() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 20, 10);
        Set<String> topics = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            topics.add(SimulatedEventFactory.next(runId, i, SimulationType.MIXED_ATTACK,
                    population, 1.0, 50, Map.of()).topic());
        }
        assertThat(topics.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void normalTrafficUsesBenignShare() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 20, 10);
        Map<String, Object> params = Map.of("eventsPerSecond", 100, "transactionsPerSecond", 25, "normalAmount", 80);
        for (int i = 0; i < 400; i++) {
            var event = SimulatedEventFactory.next(runId, i, SimulationType.NORMAL_TRAFFIC,
                    population, 1.0, 50, params);
            assertThat(event.topic()).doesNotContain("network"); // benign mix avoids sensor events
        }
    }

    @Test
    void normalTrafficMixesLoginsRequestsPaymentsOrdersLogouts() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = Map.of("eventsPerSecond", 200, "transactionsPerSecond", 40, "normalAmount", 80);
        Map<String, Long> counts = new java.util.HashMap<>();
        for (int i = 0; i < 2000; i++) {
            var payload = SimulatedEventFactory.next(runId, i, SimulationType.NORMAL_TRAFFIC,
                    population, 1.0, 50, params).payload();
            counts.merge((String) payload.get("eventType"), 1L, Long::sum);
        }
        // Every benign kind is represented and routed to its real topic.
        assertThat(counts).containsKeys("LOGIN_ATTEMPT", "API_REQUEST", "PAYMENT_AUTHORIZED",
                "ORDER_PLACED", "LOGOUT");
        // ~20% of the mix is payments (transactionsPerSecond/eventsPerSecond = 40/200).
        double paymentShare = counts.getOrDefault("PAYMENT_AUTHORIZED", 0L) / 2000.0;
        assertThat(paymentShare).isBetween(0.15, 0.25);
        // Order and logout are distinct, non-generic events with matching topics.
        assertThat(SimulatedEventFactory.topicFor("ORDER_PLACED", false, SimulationType.NORMAL_TRAFFIC))
                .isEqualTo(SimulatedEventFactory.TOPIC_RETAIL);
        assertThat(SimulatedEventFactory.topicFor("LOGOUT", false, SimulationType.NORMAL_TRAFFIC))
                .isEqualTo(SimulatedEventFactory.TOPIC_AUTH);
        assertThat(SimulatedEventFactory.topicFor("PAYMENT_AUTHORIZED", false, SimulationType.NORMAL_TRAFFIC))
                .isEqualTo(SimulatedEventFactory.TOPIC_PAYMENT);
    }

    @Test
    void normalTrafficUsesConfiguredNormalAmount() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = Map.of("eventsPerSecond", 200, "transactionsPerSecond", 100, "normalAmount", 50);
        double sum = 0;
        int payments = 0;
        for (int i = 0; i < 2000; i++) {
            var payload = SimulatedEventFactory.next(runId, i, SimulationType.NORMAL_TRAFFIC,
                    population, 1.0, 50, params).payload();
            if ("PAYMENT_AUTHORIZED".equals(payload.get("eventType"))) {
                sum += (Double) payload.get("amount");
                payments++;
            }
        }
        // Amounts are normal/2..normal*1.5 around the configured normalAmount.
        double avg = payments > 0 ? sum / payments : 0;
        assertThat(payments).isGreaterThan(300);
        assertThat(avg).isBetween(35.0, 85.0);
    }
}
