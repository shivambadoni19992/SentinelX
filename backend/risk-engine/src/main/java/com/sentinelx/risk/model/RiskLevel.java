package com.sentinelx.risk.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Risk level bands: 0–30 LOW, 31–60 MEDIUM, 61–80 HIGH, 81–100 CRITICAL.
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Maps a 0–100 score onto its band. */
    public static RiskLevel forScore(int score) {
        int s = Math.max(0, Math.min(100, score));
        if (s <= 30) {
            return LOW;
        }
        if (s <= 60) {
            return MEDIUM;
        }
        if (s <= 80) {
            return HIGH;
        }
        return CRITICAL;
    }

    /** The default action the platform should take for this level. */
    public String defaultAction() {
        return switch (this) {
            case LOW -> "ALLOW";
            case MEDIUM -> "REVIEW";
            case HIGH -> "CHALLENGE";
            case CRITICAL -> "BLOCK";
        };
    }

    public static RiskLevel of(String raw) {
        if (raw == null) {
            return LOW;
        }
        return switch (raw.toUpperCase()) {
            case "CRITICAL", "FATAL" -> CRITICAL;
            case "HIGH", "WARN", "WARNING" -> HIGH;
            case "MEDIUM", "MODERATE" -> MEDIUM;
            default -> LOW;
        };
    }

    public BigDecimal toScore(int score) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score))).setScale(2, RoundingMode.HALF_UP);
    }
}