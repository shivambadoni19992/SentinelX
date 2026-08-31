package com.sentinelx.auth.domain;

/**
 * Roles recognized by the SentinelX SOC platform.
 *
 * <p>Ordinals are never persisted; the {@link #name()} value is stored in the
 * {@code whoami.users.role} column and encoded into JWTs as
 * {@code ROLE_<NAME>} Spring authorities.
 */
public enum Role {
    ADMIN,
    SOC_ANALYST,
    SECURITY_ENGINEER,
    SUPPORT,
    AUDITOR;

    /** Spring Security authority string, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}