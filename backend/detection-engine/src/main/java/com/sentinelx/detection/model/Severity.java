package com.sentinelx.detection.model;

/**
 * Severity of a raised detection, aligned with the platform-wide scale used by
 * the security-event normalizer (LOW / MEDIUM / HIGH / CRITICAL).
 */
public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}
