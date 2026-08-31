package com.sentinelx.auth.service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sentinelx.auth.domain.AccountStatus;
import com.sentinelx.auth.dto.AuthResponse;
import com.sentinelx.auth.dto.LoginRequest;
import com.sentinelx.auth.dto.UserDto;
import com.sentinelx.auth.entity.Session;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.SessionRepository;
import com.sentinelx.auth.repository.UserRepository;
import com.sentinelx.auth.security.JwtService;

/**
 * Core authentication flows: login (with BCrypt password verification and
 * account-status enforcement) and current-user resolution for {@code /me}.
 *
 * <p>Passwords are only ever compared against stored hashes; they are never
 * returned to clients and never persisted in plaintext.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, SessionRepository sessions,
            PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticate a user and issue a signed JWT. Rejects unknown credentials
     * (401) and blocked accounts (403). A session row is recorded for audit.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = users.findByUsername(request.username())
                .or(() -> users.findByEmail(request.username()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        assertAccessible(user);

        String token = jwtService.generateToken(user);
        recordSession(user, token, ipAddress, userAgent);

        return AuthResponse.of(token, jwtService.ttl().toSeconds(), UserDto.from(user));
    }

    /**
     * Resolve the currently authenticated user by the id carried in the JWT.
     * Still enforces account status (a blocked account is refused post-login).
     */
    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        assertAccessible(user);
        return UserDto.from(user);
    }

    private void assertAccessible(User user) {
        AccountStatus status;
        try {
            status = AccountStatus.valueOf(user.getAccountStatus());
        } catch (IllegalArgumentException e) {
            status = AccountStatus.BLOCKED;
        }
        if (status == AccountStatus.BLOCKED) {
            throw new AccountBlockedException("Account is blocked");
        }
    }

    private void recordSession(User user, String token, String ipAddress, String userAgent) {
        Session session = new Session();
        session.setUserId(user.getId());
        session.setTokenHash(sha256Hex(token));
        session.setStatus("ACTIVE");
        if (ipAddress != null && !ipAddress.isBlank()) {
            try {
                session.setIpAddress(InetAddress.getByName(ipAddress));
            } catch (Exception ignored) {
                session.setIpAddress(null);
            }
        }
        session.setUserAgent(userAgent);
        session.setStartedAt(Instant.now());
        session.setLastSeenAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(Duration.ofSeconds(jwtService.ttl().toSeconds())));
        sessions.save(session);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}