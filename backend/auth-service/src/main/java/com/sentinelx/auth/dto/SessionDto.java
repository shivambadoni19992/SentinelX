package com.sentinelx.auth.dto;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.auth.entity.Session;

/** API contract for a Session (schema whoami.sessions). */
public record SessionDto(
        UUID id,
        UUID userId,
        UUID deviceId,
        String tokenHash,
        InetAddress ipAddress,
        String userAgent,
        String status,
        Instant startedAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SessionDto from(Session s) {
        return new SessionDto(s.getId(), s.getUserId(), s.getDeviceId(), s.getTokenHash(),
                s.getIpAddress(), s.getUserAgent(), s.getStatus(), s.getStartedAt(),
                s.getLastSeenAt(), s.getExpiresAt(), s.getCreatedAt(), s.getUpdatedAt());
    }

    public Session toEntity() {
        Session s = new Session();
        s.setUserId(userId);
        s.setDeviceId(deviceId);
        s.setTokenHash(tokenHash);
        s.setIpAddress(ipAddress);
        s.setUserAgent(userAgent);
        s.setStatus(status == null ? "ACTIVE" : status);
        s.setStartedAt(startedAt != null ? startedAt : Instant.now());
        s.setLastSeenAt(lastSeenAt);
        s.setExpiresAt(expiresAt);
        return s;
    }
}