package com.sentinelx.auth.dto;

/**
 * Successful login payload. Carries the signed JWT plus a non-sensitive view of
 * the authenticated user. Password material is never included.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserDto user) {

    public static AuthResponse of(String token, long ttlSeconds, UserDto user) {
        return new AuthResponse(token, "Bearer", ttlSeconds, user);
    }
}