package com.sentinelx.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.risk.entity.RiskDecision;
import com.sentinelx.risk.repository.RiskDecisionRepository;

@SpringBootTest
@Testcontainers
class RiskPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RiskDecisionRepository decisions;

    @Test
    void riskDecisionRoundTrip() {
        RiskDecision d = new RiskDecision();
        d.setSubjectId(UUID.randomUUID());
        d.setSubjectType("USER");
        d.setRiskLevel("CRITICAL");
        d.setRiskScore(new BigDecimal("92.50"));
        d.setAction("CHALLENGE");
        d.setFactors(Map.of("velocity", 40, "newDevice", true));
        d = decisions.saveAndFlush(d);

        assertThat(d.getId()).isNotNull();
        assertThat(decisions.findByRiskLevel("CRITICAL")).hasSize(1);
        RiskDecision loaded = decisions.findById(d.getId()).orElseThrow();
        assertThat(loaded.getFactors()).containsEntry("velocity", 40);
    }
}