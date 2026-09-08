package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sentinelx.simulation.engine.SimulatedEventFactory;
import com.sentinelx.simulation.engine.SimulatedEventFactory.GeneratedEvent;
import com.sentinelx.simulation.engine.SimulatedEventFactory.Population;

/**
 * Verifies the TRANSACTION_VELOCITY scenario: rapid payment bursts from a small
 * set of concurrent users that trigger the TransactionVelocityRule (5+ payments
 * in 60 seconds from the same customer).
 */
class TransactionVelocitySimulationTest {

    @Test
    @DisplayName("transactionVelocityEvent generates burst payments from concurrent users")
    void burstPaymentsComeFromConcurrentUsers() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("users", 1000);
        params.put("transactionsPerUser", 20);
        params.put("timeWindowSeconds", 60);
        params.put("amount", 500);
        params.put("concurrentUsers", 5);
        params.put("attackPercentage", 25);

        java.util.Set<String> hostileActors = new java.util.HashSet<>();
        int total = 1000;

        for (int i = 0; i < total; i++) {
            GeneratedEvent event = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 50, true, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_PAYMENT);
            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("PAYMENT_CREATED");
            String actor = String.valueOf(payload.get("actor"));
            hostileActors.add(actor);
        }

        // With concurrentUsers=5, all hostile actors should be from the burstuser0..4 set.
        assertThat(hostileActors).allMatch(a -> a.startsWith("burstuser"));
        assertThat(hostileActors.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("transactionVelocityEvent generates benign payments from normal users")
    void benignPaymentsComeFromNormalUsers() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("users", 1000);
        params.put("amount", 500);
        params.put("concurrentUsers", 5);

        java.util.Set<String> benignActors = new java.util.HashSet<>();

        for (int i = 0; i < 500; i++) {
            GeneratedEvent event = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 50, false, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_PAYMENT);
            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("PAYMENT_CREATED");
            String actor = String.valueOf(payload.get("actor"));
            benignActors.add(actor);
            assertThat(Boolean.TRUE.equals(payload.get("newDevice"))).isFalse();
            assertThat(Boolean.TRUE.equals(payload.get("suspiciousIp"))).isFalse();
        }

        assertThat(benignActors).allMatch(a -> a.startsWith("user"));
    }

    @Test
    @DisplayName("transactionVelocityEvent hostile payments have elevated amounts")
    void hostilePaymentsHaveElevatedAmounts() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("amount", 500);
        params.put("concurrentUsers", 5);
        params.put("transactionsPerUser", 20);
        params.put("timeWindowSeconds", 60);

        double maxHostileAmount = 0;
        double maxBenignAmount = 0;

        for (int i = 0; i < 500; i++) {
            GeneratedEvent hostileEvent = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 80, true, params);
            double hostileAmt = ((Number) hostileEvent.payload().get("amount")).doubleValue();
            if (hostileAmt > maxHostileAmount) maxHostileAmount = hostileAmt;

            GeneratedEvent benignEvent = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 80, false, params);
            double benignAmt = ((Number) benignEvent.payload().get("amount")).doubleValue();
            if (benignAmt > maxBenignAmount) maxBenignAmount = benignAmt;
        }

        assertThat(maxHostileAmount).isGreaterThan(maxBenignAmount);
    }

    @Test
    @DisplayName("transactionVelocityEvent routes to security.payment topic")
    void eventsRouteToPaymentTopic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("amount", 500);
        params.put("concurrentUsers", 5);

        for (int i = 0; i < 100; i++) {
            GeneratedEvent event = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 50, true, params);
            assertThat(event.topic()).isEqualTo("security.payment");
        }
    }

    @Test
    @DisplayName("transactionVelocityEvent generates deterministic correlationId for a given sequence")
    void eventsAreDeterministic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("amount", 500);
        params.put("concurrentUsers", 5);

        GeneratedEvent e1 = SimulatedEventFactory.transactionVelocityEvent(
                runId, 42, population, 50, true, params);
        GeneratedEvent e2 = SimulatedEventFactory.transactionVelocityEvent(
                runId, 42, population, 50, true, params);

        assertThat(e1.payload().get("correlationId")).isEqualTo(e2.payload().get("correlationId"));
        assertThat(e1.topic()).isEqualTo(e2.topic());
    }

    @Test
    @DisplayName("transactionVelocityEvent uses configured concurrent users")
    void usesConfiguredConcurrentUsers() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("concurrentUsers", 3);
        params.put("amount", 500);
        params.put("transactionsPerUser", 20);
        params.put("timeWindowSeconds", 60);

        java.util.Set<String> actors = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            GeneratedEvent event = SimulatedEventFactory.transactionVelocityEvent(
                    runId, i, population, 50, true, params);
            actors.add(String.valueOf(event.payload().get("actor")));
        }

        assertThat(actors).allMatch(a -> a.startsWith("burstuser"));
        assertThat(actors.size()).isLessThanOrEqualTo(3);
    }
}