package com.sentinelx.securityevent.dto;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.securityevent.entity.SecurityEvent;

/** API contract for a SecurityEvent. */
public record SecurityEventDto(
        UUID id,
        String eventType,
        UUID userId,
        UUID deviceId,
        UUID sessionId,
        String actor,
        String action,
        String outcome,
        String severity,
        InetAddress sourceIp,
        Map<String, Object> metadata,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SecurityEventDto from(SecurityEvent e) {
        return new SecurityEventDto(e.getId(), e.getEventType(), e.getUserId(), e.getDeviceId(),
                e.getSessionId(), e.getActor(), e.getAction(), e.getOutcome(), e.getSeverity(),
                e.getSourceIp(), e.getMetadata(), e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public SecurityEvent toEntity() {
        SecurityEvent e = new SecurityEvent();
        e.setEventType(eventType);
        e.setUserId(userId);
        e.setDeviceId(deviceId);
        e.setSessionId(sessionId);
        e.setActor(actor);
        e.setAction(action);
        e.setOutcome(outcome == null ? "UNKNOWN" : outcome);
        e.setSeverity(severity == null ? "LOW" : severity);
        e.setSourceIp(sourceIp);
        if (metadata != null) {
            e.setMetadata(metadata);
        }
        e.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        return e;
    }
}