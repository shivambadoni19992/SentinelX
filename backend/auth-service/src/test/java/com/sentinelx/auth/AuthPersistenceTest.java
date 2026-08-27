package com.sentinelx.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.auth.entity.Device;
import com.sentinelx.auth.entity.Session;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.DeviceRepository;
import com.sentinelx.auth.repository.SessionRepository;
import com.sentinelx.auth.repository.UserRepository;

/**
 * Boots auth-service against a real PostgreSQL (Testcontainers). Verifies that
 * the Flyway migration (schema whoami) applies and that the mapped entities
 * round-trip. Because ddl-auto is {@code validate}, a mismatch between the
 * entities and the V1 migration would fail startup.
 */
@SpringBootTest
@Testcontainers
class AuthPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository users;

    @Autowired
    DeviceRepository devices;

    @Autowired
    SessionRepository sessions;

    @Test
    void userDeviceSessionRoundTrip() throws Exception {
        User u = new User();
        u.setUsername("alice");
        u.setEmail("alice@sentinelx.test");
        u.setPasswordHash("hash");
        u.setRole("ADMIN");
        u = users.saveAndFlush(u);
        assertThat(u.getId()).isNotNull();

        Device d = new Device();
        d.setUserId(u.getId());
        d.setDeviceFingerprint("fp-123");
        d.setDeviceType("MOBILE");
        d.setIpAddress(InetAddress.getByName("203.0.113.9"));
        d = devices.saveAndFlush(d);
        assertThat(d.getId()).isNotNull();
        assertThat(devices.findByUserId(u.getId())).hasSize(1);

        Session s = new Session();
        s.setUserId(u.getId());
        s.setDeviceId(d.getId());
        s.setTokenHash("tok-abc");
        s = sessions.saveAndFlush(s);
        assertThat(s.getId()).isNotNull();
        assertThat(sessions.findByUserId(u.getId())).hasSize(1);

        assertThat(users.findByUsername("alice")).isPresent();
        assertThat(users.findByEmail("alice@sentinelx.test")).isPresent();
    }
}