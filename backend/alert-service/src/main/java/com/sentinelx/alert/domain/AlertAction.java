package com.sentinelx.alert.domain;

/**
 * Response actions an analyst can apply to an alert. Every action changes
 * actual application state (payment status, account status, Redis rate-limit
 * flags) and emits an audit event.
 */
public enum AlertAction {
    ALLOW,
    MONITOR,
    REQUIRE_VERIFICATION,
    HOLD_TRANSACTION,
    BLOCK_ACCOUNT,
    RATE_LIMIT;

    public static AlertAction of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        return AlertAction.valueOf(raw.trim().toUpperCase());
    }
}