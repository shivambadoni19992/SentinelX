package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sentinelx.simulation.domain.SimulationConfig;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.engine.SimulatedEventFactory;
import com.sentinelx.simulation.engine.SimulatedEventFactory.GeneratedEvent;
import com.sentinelx.simulation.engine.SimulatedEventFactory.Population;

/**
 * Verifies the PAYMENT_FRAUD scenario: event generation with the full parameter
 * set and that the generated events carry the attributes the detection engine needs.
 */
class PaymentFraudSimulationTest {

    @Test
    @DisplayName("paymentFraudEvent generates hostile payments with all fraud attributes")
    void hostilePaymentsCarryFraudAttributes() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("normalAmount", 80.0);
        params.put("suspiciousAmount", 1_200.0);
        params.put("highValueAmount", 100_000.0);
        params.put("failedPaymentPercentage", 35.0);
        params.put("newDevicePercentage", 30.0);
        params.put("suspiciousIpPercentage", 40.0);
        params.put("velocity", 10.0);

        int highValue = 0, declined = 0, newDevice = 0, suspiciousIp = 0;
        int total = 2000;

        for (int i = 0; i < total; i++) {
            GeneratedEvent event = SimulatedEventFactory.paymentFraudEvent(
                    runId, i, population, 50, true, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_PAYMENT);

            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("PAYMENT_CREATED");

            double amt = ((Number) payload.get("amount")).doubleValue();
            if (amt >= 100_000.0 * 0.8) highValue++;
            if ("DECLINED".equals(payload.get("outcome"))) declined++;
            if (Boolean.TRUE.equals(payload.get("newDevice"))) newDevice++;
            if (Boolean.TRUE.equals(payload.get("suspiciousIp"))) suspiciousIp++;
        }

        assertThat(highValue).isGreaterThan(0);
        assertThat(declined).isGreaterThan(0);
        assertThat(newDevice).isGreaterThan(0);
        assertThat(suspiciousIp).isGreaterThan(0);
    }

    @Test
    @DisplayName("paymentFraudEvent generates benign payments without fraud attributes")
    void benignPaymentsAreClean() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("normalAmount", 80.0);
        params.put("highValueAmount", 100_000.0);
        params.put("velocity", 10.0);

        for (int i = 0; i < 500; i++) {
            GeneratedEvent event = SimulatedEventFactory.paymentFraudEvent(
                    runId, i, population, 50, false, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_PAYMENT);
            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("PAYMENT_CREATED");
            double amt = ((Number) payload.get("amount")).doubleValue();
            assertThat(amt).isLessThan(1_000);
            assertThat(Boolean.TRUE.equals(payload.get("newDevice"))).isFalse();
            assertThat(Boolean.TRUE.equals(payload.get("suspiciousIp"))).isFalse();
        }
    }

    @Test
    @DisplayName("paymentFraudEvent concentrates hostile traffic on fraud-ring cards")
    void hostileTrafficUsesFraudRingCards() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("velocity", 5.0);
        params.put("highValueAmount", 100_000.0);

        java.util.Set<String> actors = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            GeneratedEvent event = SimulatedEventFactory.paymentFraudEvent(
                    runId, i, population, 50, true, params);
            actors.add(String.valueOf(event.payload().get("actor")));
        }
        assertThat(actors).allMatch(a -> a.startsWith("fraudcard"));
        assertThat(actors.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("paymentFraudEvent routes to security.payment topic")
    void eventsRouteToPaymentTopic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("highValueAmount", 100_000.0);

        for (int i = 0; i < 100; i++) {
            GeneratedEvent event = SimulatedEventFactory.paymentFraudEvent(
                    runId, i, population, 50, true, params);
            assertThat(event.topic()).isEqualTo("security.payment");
        }
    }

    @Test
    @DisplayName("paymentFraudEvent generates deterministic correlationId for a given sequence")
    void eventsAreDeterministic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(100, 50, 30);
        Map<String, Object> params = new HashMap<>();
        params.put("highValueAmount", 100_000.0);
        params.put("velocity", 10.0);

        GeneratedEvent e1 = SimulatedEventFactory.paymentFraudEvent(
                runId, 42, population, 50, true, params);
        GeneratedEvent e2 = SimulatedEventFactory.paymentFraudEvent(
                runId, 42, population, 50, true, params);

        assertThat(e1.payload().get("correlationId")).isEqualTo(e2.payload().get("correlationId"));
        assertThat(e1.topic()).isEqualTo(e2.topic());
    }
}