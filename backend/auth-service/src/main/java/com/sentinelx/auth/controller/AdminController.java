package com.sentinelx.auth.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only diagnostics. Demonstrates role-based authorization with
 * {@code @PreAuthorize}; the role is read from the validated JWT.
 */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminController {

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> dashboard(Authentication authentication) {
        return Map.of(
                "status", "ok",
                "role", "ADMIN",
                "time", Instant.now().toString());
    }
}