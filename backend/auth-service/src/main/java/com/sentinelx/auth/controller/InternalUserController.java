package com.sentinelx.auth.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelx.auth.domain.AccountStatus;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.UserRepository;

/**
 * Internal (service-to-service) identity endpoints used by the alert
 * service's response actions (e.g. BLOCK_ACCOUNT). Permitted without a JWT
 * in {@code WebSecurityConfig}; not exposed through the gateway.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository users;

    public InternalUserController(UserRepository users) {
        this.users = users;
    }

    /** Sets a user's {@link AccountStatus} (e.g. BLOCKED) from a response action. */
    @PostMapping("/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        User user = users.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        AccountStatus status;
        try {
            status = AccountStatus.valueOf(
                    body.getOrDefault("status", "").trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status must be one of ACTIVE, MONITORED, BLOCKED"));
        }
        user.setAccountStatus(status.name());
        users.save(user);
        return ResponseEntity.ok(Map.of(
                "userId", user.getId().toString(),
                "accountStatus", user.getAccountStatus(),
                "reason", body.getOrDefault("reason", "")));
    }
}