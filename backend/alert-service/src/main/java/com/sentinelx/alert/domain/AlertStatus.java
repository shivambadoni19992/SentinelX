package com.sentinelx.alert.domain;

/** Review lifecycle of a security alert. */
public enum AlertStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE;

    public static AlertStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        return AlertStatus.valueOf(raw.trim().toUpperCase());
    }
}