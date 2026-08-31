package com.sentinelx.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.auth.domain.AccountStatus;
import com.sentinelx.auth.domain.Role;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.SessionRepository;
import com.sentinelx.auth.repository.UserRepository;
import com.sentinelx.auth.security.JwtService;

/**
 * End-to-end authentication tests: full Spring context + real Postgres
 * (Testcontainers) + MockMvc through the actual security filter chain.
 *
 * Covers: valid/invalid login, invalid JWT, expired JWT, role authorization,
 * and blocked-account enforcement — plus the guarantee that password material
 * is never returned or stored in plaintext.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiTest {

    private static final String PASSWORD = "Test_123!";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Fixed signing secret so tests can mint tokens with the same key.
        registry.add("sentinelx.jwt.secret", () -> "test-only-secret-0123456789abcdefghIJKLMNOP");
        registry.add("sentinelx.jwt.ttl-seconds", () -> "3600");
        registry.add("sentinelx.jwt.issuer", () -> "sentinelx-auth-service");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @BeforeEach
    void cleanAndSeed() {
        sessions.deleteAll();
        users.deleteAll();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private User createUser(String username, Role role, AccountStatus status) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@sentinelx.test");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(role.name());
        u.setAccountStatus(status.name());
        return users.saveAndFlush(u);
    }

    private String loginToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("username", username, "password", PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
// ---------------------------------------------------------------- login

    @Test
    void validLoginReturnsTokenAndUser() throws Exception {
        createUser("alice", Role.SOC_ANALYST, AccountStatus.ACTIVE);

        String body = objectMapper.writeValueAsString(
                Map.of("username", "alice", "password", PASSWORD));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.role").value("SOC_ANALYST"))
                .andExpect(jsonPath("$.user.accountStatus").value("ACTIVE"));
    }

    @Test
    void loginAcceptsEmailIdentifier() throws Exception {
        createUser("bob", Role.AUDITOR, AccountStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "bob@sentinelx.test", "password", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void invalidPasswordReturns401() throws Exception {
        createUser("carol", Role.SOC_ANALYST, AccountStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "carol", "password", "wrong-password"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void unknownUserReturns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("username", "ghost", "password", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsCaseSensitiveOnPassword() throws Exception {
        createUser("dave", Role.SUPPORT, AccountStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "dave", "password", PASSWORD.toLowerCase()));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankCredentialsReturn400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("username", "", "password", ""));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
// ---------------------------------------------------------------- /me

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        createUser("erin", Role.ADMIN, AccountStatus.ACTIVE);
        String token = loginToken("erin");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("erin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithInvalidJwtReturns401() throws Exception {
        String garbage = "not.a.real.jwt";
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + garbage))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredJwtReturns401() throws Exception {
        User user = createUser("frank", Role.SOC_ANALYST, AccountStatus.ACTIVE);
        String expired = jwtService.generateToken(user, Duration.ofSeconds(-60));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithWrongIssuerReturns401() throws Exception {
        User user = createUser("grace", Role.SOC_ANALYST, AccountStatus.ACTIVE);
        // Token signed by an impostor service won't carry our issuer.
        var impostor = new com.sentinelx.auth.security.JwtService(
                "test-only-secret-0123456789abcdefghIJKLMNOP", "impostor", 3600);
        String token = impostor.generateToken(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
// ------------------------------------------------------- role authorization

    @Test
    void adminCanAccessAdminDashboard() throws Exception {
        createUser("hannah", Role.ADMIN, AccountStatus.ACTIVE);
        String token = loginToken("hannah");
        mockMvc.perform(get("/api/auth/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void nonAdminCannotAccessAdminDashboard() throws Exception {
        createUser("ian", Role.SOC_ANALYST, AccountStatus.ACTIVE);
        String token = loginToken("ian");
        mockMvc.perform(get("/api/auth/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorEngineerAndSupportCannotAccessAdminDashboard() throws Exception {
        for (Role role : new Role[] { Role.AUDITOR, Role.SUPPORT, Role.SECURITY_ENGINEER }) {
            createUser("u_" + role.name(), role, AccountStatus.ACTIVE);
            String token = loginToken("u_" + role.name());
            mockMvc.perform(get("/api/auth/admin/dashboard")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------ account status

    @Test
    void blockedAccountCannotLogin() throws Exception {
        createUser("james", Role.SOC_ANALYST, AccountStatus.BLOCKED);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "james", "password", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void blockedAccountIsRefusedOnMeEvenWithValidToken() throws Exception {
        User blocked = createUser("kim", Role.SOC_ANALYST, AccountStatus.BLOCKED);
        String token = jwtService.generateToken(blocked);
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void monitoredAccountCanLogin() throws Exception {
        createUser("leila", Role.SOC_ANALYST, AccountStatus.MONITORED);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "leila", "password", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.accountStatus").value("MONITORED"));
    }

    // -------------------------------------------------- password material safety

    @Test
    void passwordIsStoredHashedNotPlaintext() {
        User u = createUser("mia", Role.ADMIN, AccountStatus.ACTIVE);
        String stored = users.findById(u.getId()).orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo(PASSWORD);
        assertThat(stored).startsWith("$2");          // bcrypt $2a/$2b/$2y
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
        assertThat(passwordEncoder.matches("wrong", stored)).isFalse();
    }

    @Test
    void noPasswordMaterialInLoginResponse() throws Exception {
        createUser("nate", Role.SECURITY_ENGINEER, AccountStatus.ACTIVE);
        String body = objectMapper.writeValueAsString(
                Map.of("username", "nate", "password", PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        assertThat(response.toLowerCase()).doesNotContain("password");
        assertThat(response.toLowerCase()).doesNotContain("password_hash");
    }

    @Test
    void loginPersistsAnAuditSessionRow() throws Exception {
        createUser("olive", Role.SUPPORT, AccountStatus.ACTIVE);
        loginToken("olive");
        assertThat(sessions.findAll()).hasSize(1);
    }
}