package com.sentinelx.securityevent.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.securityevent.entity.AuditLog;

/** API contract for an AuditLog entry. */
public record AuditLogDto(
        UUID id,
        UUID userId,
        String action,
        String actor,
        String resourceType,
        UUID resourceId,
        String result,
        Map<String, Object> details,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getUserId(), a.getAction(), a.getActor(),
                a.getResourceType(), a.getResourceId(), a.getResult(), a.getDetails(),
                a.getOccurredAt(), a.getCreatedAt(), a.getUpdatedAt());
    }

    public AuditLog toEntity() {
        AuditLog a = new AuditLog();
        a.setUserId(userId);
        a.setAction(action);
        a.setActor(actor);
        a.setResourceType(resourceType);
        a.setResourceId(resourceId);
        a.setResult(result);
        if (details != null) {
            a.setDetails(details);
        }
        a.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        return a;
    }
}