package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sentinelx.simulation.engine.SimulatedEventFactory;
import com.sentinelx.simulation.engine.SimulatedEventFactory.GeneratedEvent;
import com.sentinelx.simulation.engine.SimulatedEventFactory.Population;

/**
 * Verifies the BOT_ACTIVITY scenario: a bot fleet (dedicated source IPs + a
 * bot user-agent pattern) hammering the target endpoints at a per-bot RPS,
 * mixed with legitimate users. Bot events must carry a bot user-agent and be
 * flagged for the BotActivityRule to detect; legitimate events must look like
 * browsers.
 */
class BotActivitySimulationTest {

    @Test
    @DisplayName("botActivityEvent generates bot requests with the configured user-agent and IPs")
    void botRequestsCarryBotSignature() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 30, 20);
        Map<String, Object> params = new HashMap<>();
        params.put("botCount", 20);
        params.put("botUserAgentPattern", "CustomBot/1.0");
        params.put("targetEndpoints", "/api/products,  /api/search");

        Set<String> ips = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            GeneratedEvent event = SimulatedEventFactory.botActivityEvent(runId, i, population, 50, true, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_API);
            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("API_REQUEST");
            String ua = String.valueOf(payload.get("userAgent"));
            assertThat(ua).contains("CustomBot");
            assertThat(payload.get("path")).isIn("/api/products", "/api/search");
            assertThat(Boolean.TRUE.equals(payload.get("botRequest"))).isTrue();
            ips.add(String.valueOf(payload.get("sourceIp")));
        }
        assertThat(ips).allMatch(ip -> ip.startsWith("203.0.113."));
    }

    @Test
    @DisplayName("botActivityEvent generates benign browser traffic for legitimate users")
    void legitRequestsLookLikeBrowsers() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 30, 20);
        Map<String, Object> params = new HashMap<>();
        params.put("botCount", 20);

        for (int i = 0; i < 200; i++) {
            GeneratedEvent event = SimulatedEventFactory.botActivityEvent(runId, i, population, 50, false, params);
            assertThat(event.topic()).isEqualTo(SimulatedEventFactory.TOPIC_API);
            Map<String, Object> payload = event.payload();
            assertThat(payload.get("eventType")).isEqualTo("API_REQUEST");
            assertThat(Boolean.TRUE.equals(payload.get("botRequest"))).isFalse();
            assertThat(String.valueOf(payload.get("userAgent"))).contains("Mozilla");
        }
    }

    @Test
    @DisplayName("botActivityEvent uses the configured bot fleet size for actor naming")
    void botsScopedToFleetSize() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 30, 20);
        Map<String, Object> params = new HashMap<>();
        params.put("botCount", 5);

        Set<String> actors = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            GeneratedEvent event = SimulatedEventFactory.botActivityEvent(runId, i, population, 50, true, params);
            actors.add(String.valueOf(event.payload().get("actor")));
        }
        assertThat(actors).allMatch(a -> a.startsWith("botclient"));
        assertThat(actors.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("botActivityEvent routes to the API topic with deterministic correlation ids")
    void routesAndDeterministic() {
        UUID runId = UUID.randomUUID();
        Population population = new Population(50, 30, 20);
        Map<String, Object> params = new HashMap<>();
        params.put("botCount", 20);

        GeneratedEvent a = SimulatedEventFactory.botActivityEvent(runId, 42, population, 50, true, params);
        GeneratedEvent b = SimulatedEventFactory.botActivityEvent(runId, 42, population, 50, true, params);
        assertThat(a.topic()).isEqualTo("security.api");
        assertThat(a.payload().get("correlationId")).isEqualTo(b.payload().get("correlationId"));
    }
}