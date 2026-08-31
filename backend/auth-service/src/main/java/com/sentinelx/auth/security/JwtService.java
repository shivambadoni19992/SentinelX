package com.sentinelx.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sentinelx.auth.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates signed JWT access tokens (HS256).
 *
 * <p>Parsing implicitly verifies the signature (via the configured secret) and
 * the {@code exp} claim: any tampered or expired token throws an
 * {@link io.jsonwebtoken.JwtException}, which the filter maps to a 401.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration ttl;

    public JwtService(
            @Value("${sentinelx.jwt.secret}") String secret,
            @Value("${sentinelx.jwt.issuer}") String issuer,
            @Value("${sentinelx.jwt.ttl-seconds}") long ttlSeconds) {
        // The secret is used as raw UTF-8 bytes; it must be >= 32 bytes for HS256.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** Issue a token for {@code user} using the configured TTL. */
    public String generateToken(User user) {
        return generateToken(user, ttl);
    }

    /**
     * Issue a token for {@code user} with an explicit lifetime (used by tests to
     * mint already-expired tokens).
     */
    public String generateToken(User user, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .id(UUID.randomUUID().toString()) // unique jti so identical iat collisions can't collide
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("accountStatus", user.getAccountStatus())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate a token and return its claims. Throws
     * {@link io.jsonwebtoken.JwtException} if the signature, issuer, or
     * expiration is invalid.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Tap for validating signature keys in tests / tooling. */
    public String subject(String token) {
        return parse(token).getSubject();
    }

    public String issuer() {
        return issuer;
    }

    public Duration ttl() {
        return ttl;
    }
}