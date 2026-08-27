package com.sentinelx.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.simulation.entity.SimulationRun;
import com.sentinelx.simulation.repository.SimulationRunRepository;

@SpringBootTest
@Testcontainers
class SimulationPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SimulationRunRepository runs;

    @Test
    void simulationRunRoundTrip() {
        SimulationRun s = new SimulationRun();
        s.setName("Brute force drill");
        s.setScenario("BRUTE_FORCE");
        s.setConfig(Map.of("targets", 500, "ramp", "slow"));
        s.setStartedAt(Instant.now());
        s = runs.saveAndFlush(s);

        assertThat(s.getId()).isNotNull();
        assertThat(runs.findByStatus("PENDING")).hasSize(1);
        SimulationRun loaded = runs.findById(s.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Brute force drill");
    }
}