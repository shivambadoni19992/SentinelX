package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sentinelx.simulation.domain.DockerTopology;
import com.sentinelx.simulation.domain.SimulationType;
import com.sentinelx.simulation.engine.SimulatedEventFactory;
import com.sentinelx.simulation.engine.SimulatedEventFactory.GeneratedEvent;
import com.sentinelx.simulation.engine.SimulatedEventFactory.Population;

/**
 * Verifies the NETWORK_* drills: synthetic network observations that target
 * only Docker Compose containers (never external systems), honour the
 * configured ports, and route onto security.network.
 */
class NetworkSimulationTest {

    private Map<String, Object> params() {
        Map<String, Object> p = new HashMap<>();
        p.put("sourceContainers", "api-gateway, auth-service");
        p.put("targetContainers", "postgres, redis, kafka");
        p.put("ports", "5432,6379,9092");
        p.put("attempts", 500);
        p.put("connectionsPerSecond", 30);
        p.put("networkDurationSeconds", 60);
        return p;
    }

    @Test
    @DisplayName("network observations stay inside the compose topology (internal IPs, flagged internal)")
    void targetsStayInternal() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(20, 10, 8);
        for (SimulationType type : java.util.List.of(SimulationType.PORT_SCAN,
                SimulationType.CONNECTION_SPIKE, SimulationType.FAILED_CONNECTIONS,
                SimulationType.SUSPICIOUS_OUTBOUND)) {
            for (int i = 0; i < 50; i++) {
                GeneratedEvent event = SimulatedEventFactory.networkObservationEvent(
                        runId, i, population, type, 50, params());
                assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_NETWORK);
                Map<String, Object> payload = event.payload();
                assertThat(payload.get("eventType")).isEqualTo("NETWORK_OBSERVATION");
                assertThat(DockerTopology.isInternalDestinationIp(String.valueOf(payload.get("destinationIp"))))
                        .as("destination must be compose-internal for type " + type)
                        .isTrue();
                assertThat(Boolean.TRUE.equals(payload.get("internal"))).isTrue();
                assertThat(String.valueOf(payload.get("sourceContainer")))
                        .isIn("sentinelx-api-gateway", "sentinelx-auth-service");
                assertThat(((Number) payload.get("destinationPort")).intValue())
                        .isIn(5432, 6379, 9092);
                assertThat(String.valueOf(payload.get("protocol"))).isIn("TCP", "UDP");
            }
        }
    }

    @Test
    @DisplayName("external or unknown targets are rejected by the topology allow-list")
    void externalTargetsRejected() {
        assertThatThrownBy(() -> DockerTopology.resolve("evil-external-host"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("may only target Docker Compose containers");
        assertThatThrownBy(() -> DockerTopology.resolve("8.8.8.8"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DockerTopology.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("port-scan sweeps use the configured port range")
    void portScanSweepsPorts() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(20, 10, 8);
        Map<String, Object> p = params();
        p.put("ports", 100); // numeric count → sweep 1..100

        Set<Integer> seenPorts = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 300; i++) {
            GeneratedEvent event = SimulatedEventFactory.networkObservationEvent(
                    runId, i, population, SimulationType.PORT_SCAN, 50, p);
            seenPorts.add(((Number) event.payload().get("destinationPort")).intValue());
        }
        assertThat(seenPorts).allSatisfy(port -> assertThat(port).isBetween(1, 100));
        assertThat(seenPorts.size()).isGreaterThan(10); // sweep covers many ports
    }

    @Test
    @DisplayName("type-specific actions map correctly, outbound stays internal")
    void typeSpecificActions() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(20, 10, 8);
        assertThat(SimulatedEventFactory.networkObservationEvent(runId, 1, population,
                SimulationType.PORT_SCAN, 50, params()).payload().get("action")).isEqualTo("port_probe");
        assertThat(SimulatedEventFactory.networkObservationEvent(runId, 1, population,
                SimulationType.CONNECTION_SPIKE, 50, params()).payload().get("action")).isEqualTo("connect");
        assertThat(SimulatedEventFactory.networkObservationEvent(runId, 1, population,
                SimulationType.FAILED_CONNECTIONS, 50, params()).payload().get("action")).isEqualTo("failed_connect");
        GeneratedEvent outbound = SimulatedEventFactory.networkObservationEvent(runId, 1, population,
                SimulationType.SUSPICIOUS_OUTBOUND, 50, params());
        assertThat(outbound.payload().get("action")).isEqualTo("outbound_transfer");
        assertThat(outbound.payload().get("direction")).isEqualTo("outbound");
        assertThat((long) outbound.payload().get("bytesTransferred")).isGreaterThanOrEqualTo(100_000_000L);
        // Even the "suspicious" outbound transfer never leaves the compose subnet.
        assertThat(DockerTopology.isInternalDestinationIp(
                String.valueOf(outbound.payload().get("destinationIp")))).isTrue();
    }

    @Test
    @DisplayName("events are deterministic per sequence")
    void deterministic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(20, 10, 8);
        GeneratedEvent a = SimulatedEventFactory.networkObservationEvent(runId, 42, population,
                SimulationType.PORT_SCAN, 50, params());
        GeneratedEvent b = SimulatedEventFactory.networkObservationEvent(runId, 42, population,
                SimulationType.PORT_SCAN, 50, params());
        assertThat(a.payload().get("correlationId")).isEqualTo(b.payload().get("correlationId"));
        assertThat(a.topic()).isEqualTo(b.topic());
    }
}