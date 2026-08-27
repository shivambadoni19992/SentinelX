package com.sentinelx.auth.dto;

import java.time.Instant;
import java.util.UUID;
import com.sentinelx.auth.entity.User;

/**
 * API contract for a User. Never exposes the entity directly; password material
 * is intentionally excluded from responses.
 */
public record UserDto(
        UUID id,
        String username,
        String email,
        String role,
        String accountStatus,
        Instant createdAt,
        Instant updatedAt) {

    /** Response mapping from the entity. */
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole(), u.getAccountStatus(), u.getCreatedAt(), u.getUpdatedAt());
    }

    /** Request mapping into a new entity (id/timestamps are generated). */
    public User toEntity() {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setRole(role == null ? "CUSTOMER" : role);
        u.setAccountStatus(accountStatus == null ? "ACTIVE" : accountStatus);
        return u;
    }
}