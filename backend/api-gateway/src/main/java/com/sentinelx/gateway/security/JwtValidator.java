package com.sentinelx.gateway.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Stateless JWT validation at the edge. Uses the same {@code sentinelx.jwt.*}
 * configuration as the auth-service so tokens issued at login can be verified
 * here. Any tampered, incorrectly-signed, expired, or wrong-issuer token is
 * rejected.
 */
@Component
public class JwtValidator {

    private final SecretKey signingKey;
    private final String issuer;

    public JwtValidator(
            @Value("${sentinelx.jwt.secret}") String secret,
            @Value("${sentinelx.jwt.issuer}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    /**
     * Validate a bearer token. Returns the parsed claims on success, or {@code
     * null} when the signature, issuer, or expiration is invalid.
     */
    public Claims claimsOrNull(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        Claims claims = claimsOrNull(token);
        return claims != null && claims.getSubject() != null && !claims.getSubject().isBlank();
    }
}