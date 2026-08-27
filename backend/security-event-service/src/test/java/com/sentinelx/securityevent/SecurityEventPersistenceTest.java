package com.sentinelx.securityevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.time.Instant;
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

import com.sentinelx.securityevent.entity.AuditLog;
import com.sentinelx.securityevent.entity.SecurityEvent;
import com.sentinelx.securityevent.repository.AuditLogRepository;
import com.sentinelx.securityevent.repository.SecurityEventRepository;

@SpringBootTest
@Testcontainers
class SecurityEventPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SecurityEventRepository events;

    @Autowired
    AuditLogRepository audit;

    @Test
    void securityEventAndAuditLogRoundTrip() throws Exception {
        SecurityEvent e = new SecurityEvent();
        e.setEventType("LOGIN_ATTEMPT");
        e.setUserId(UUID.randomUUID());
        e.setSeverity("HIGH");
        e.setOutcome("BLOCKED");
        e.setSourceIp(InetAddress.getByName("198.51.100.7"));
        e.setOccurredAt(Instant.now());
        e.setMetadata(Map.of("attempts", 12, "mfa", false));
        e = events.saveAndFlush(e);
        assertThat(e.getId()).isNotNull();
        assertThat(events.findByEventType("LOGIN_ATTEMPT")).hasSize(1);
        assertThat(events.findById(e.getId()).orElseThrow().getMetadata()).containsEntry("attempts", 12);

        AuditLog a = new AuditLog();
        a.setUserId(UUID.randomUUID());
        a.setAction("USER_UPDATE");
        a.setResourceType("USER");
        a.setDetails(Map.of("field", "role"));
        a = audit.saveAndFlush(a);
        assertThat(a.getId()).isNotNull();
        assertThat(audit.findByResourceType("USER")).hasSize(1);
    }
}