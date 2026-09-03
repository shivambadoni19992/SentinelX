package com.sentinelx.payment.service;

/**
 * Egress masking for sensitive financial/session data.
 *
 * <p>Device identifiers and IP addresses are stored in full (they are needed
 * for fraud analysis) but must never leave the service unmasked — neither in
 * API responses nor in log lines. All masking here is deterministic so the
 * same value always renders identically across requests, screens and logs.
 */
public final class DataMasker {

    private DataMasker() {
    }

    /** IPv4 {@code 203.0.113.45} → {@code 203.0.113.xxx}; IPv6 keeps its first 2 hextets. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.contains(".")) {
            int lastDot = ip.lastIndexOf('.');
            return ip.substring(0, lastDot + 1) + "xxx";
        }
        // IPv6: keep the first two hextets.
        String[] parts = ip.split(":", 3);
        if (parts.length < 2) {
            return "***";
        }
        return parts[0] + ":" + parts[1] + ":****";
    }

    /** {@code device-9f3ab21c} → {@code 9f3a****}; non-alphanumeric prefixes are dropped. */
    public static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }
        String cleaned = deviceId.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() <= 4) {
            return "****";
        }
        return cleaned.substring(0, 4) + "****";
    }

    /** Keep the last 4 characters of a card/PAN-like value: {@code ****4242}. */
    public static String maskTail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }
}