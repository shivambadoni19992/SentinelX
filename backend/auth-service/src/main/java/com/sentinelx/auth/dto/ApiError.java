package com.sentinelx.auth.dto;

import java.time.Instant;

/** Uniform JSON error payload returned by the auth-service exception handler. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}