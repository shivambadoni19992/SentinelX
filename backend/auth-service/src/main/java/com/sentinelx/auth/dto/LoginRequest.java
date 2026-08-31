package com.sentinelx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials submitted to {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 64)
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 128)
        String password) {
}