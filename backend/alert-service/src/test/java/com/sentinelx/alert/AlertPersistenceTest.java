package com.sentinelx.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;

@SpringBootTest
@Testcontainers
class AlertPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SecurityAlertRepository alerts;

    @Test
    void securityAlertRoundTrip() {
        SecurityAlert a = new SecurityAlert();
        a.setTitle("Suspicious login");
        a.setSeverity("HIGH");
        a.setEntityType("USER");
        a.setEventId(UUID.randomUUID());
        a = alerts.saveAndFlush(a);

        assertThat(a.getId()).isNotNull();
        assertThat(alerts.findBySeverity("HIGH")).hasSize(1);
        assertThat(alerts.findByStatus("OPEN")).hasSize(1);
    }
}