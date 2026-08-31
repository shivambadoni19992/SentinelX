package com.sentinelx.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Gateway edge behavior against a real Redis (Testcontainers): JWT enforcement,
 * correlation-id propagation, security headers, and Redis-backed rate limiting.
 *
 * <p>Protected routes reject with 401 before any upstream routing, so these
 * tests do not require a live backend service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "rate-limit.burst=2",
        "rate-limit.replenish=2",
        "sentinelx.jwt.secret=gw-test-secret-0123456789abcdefghijklmnopqrstuv",
        "sentinelx.jwt.issuer=sentinelx-auth-service"
})
class GatewaySecurityIntegrationTest {

    private static final String SECRET = "gw-test-secret-0123456789abcdefghijklmnopqrstuv";
    private static final String ISSUER = "sentinelx-auth-service";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }

    // ------------------------------------------------------------- JWT

    @Test
    void protectedRouteWithoutTokenRejected401() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void protectedRouteWithInvalidTokenRejected401() {
        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithExpiredTokenRejected401() {
        String expired = sign("00000000-0000-0000-0000-000000000001",
                new Date(System.currentTimeMillis() - 60_000));
        client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithValidTokenPassesJwtGate() {
        // A valid token passes the edge JWT check; routing then fails only
        // because no live upstream exists in this test (never 401).
        String token = sign("00000000-0000-0000-0000-000000000001",
                new Date(System.currentTimeMillis() + 60_000));
        int status = client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(429);
    }

    @Test
    void loginRouteIsPublic() {
        // Public login bypasses JWT (never 401). Whether it 502s on the missing
        // upstream or 429s on the shared test bucket doesn't matter here.
        int status = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"x\",\"password\":\"y\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
        assertThat(status).isNotEqualTo(401);
    }

    // ------------------------------------------------------ headers / logging

    @Test
    void responsesCarryCorrelationIdAndSecurityHeaders() {
        client.get().uri("/api/auth/me") // 401 but headers are still attached
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches("X-Correlation-Id", ".+")
                .expectHeader().valueMatches("X-Content-Type-Options", "nosniff")
                .expectHeader().valueMatches("X-Frame-Options", "DENY")
                .expectHeader().valueMatches("X-XSS-Protection", "1; mode=block")
                .expectHeader().exists("Strict-Transport-Security");
    }

    @Test
    void correlationIdIsPropagatedFromClient() {
        String cid = "client-supplied-correlation-123";
        client.get().uri("/api/auth/me")
                .header("X-Correlation-Id", cid)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", cid);
    }

    // ------------------------------------------------------ rate limiting

    @Test
    void redisRateLimiterRejectsExcessTraffic() {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            int status = client.post().uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"username\":\"x\",\"password\":\"y\"}")
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value();
            statuses.add(status);
        }
        assertThat(statuses).describedAs("burst capacity should be exceeded")
                .contains(429);
    }

    // ------------------------------------------------------------- helpers

    private String sign(String subject, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}