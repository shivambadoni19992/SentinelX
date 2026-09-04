package com.sentinelx.alert.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.sentinelx.alert.entity.SecurityAlert;

/** API contract for a SecurityAlert — entities stay behind the DTO boundary. */
public record SecurityAlertDto(
        UUID id,
        String title,
        String description,
        String severity,
        String entityType,
        UUID entityId,
        UUID eventId,
        String status,
        String assignedTo,
        String action,
        String actor,
        Map<String, Object> actionDetail,
        Instant triggeredAt,
        Instant createdAt,
        Instant updatedAt) {

    public static SecurityAlertDto from(SecurityAlert a) {
        return new SecurityAlertDto(a.getId(), a.getTitle(), a.getDescription(), a.getSeverity(),
                a.getEntityType(), a.getEntityId(), a.getEventId(), a.getStatus(), a.getAssignedTo(),
                a.getAction(), a.getActor(), a.getActionDetail(),
                a.getTriggeredAt(), a.getCreatedAt(), a.getUpdatedAt());
    }

    public SecurityAlert toEntity() {
        SecurityAlert a = new SecurityAlert();
        a.setTitle(title);
        a.setDescription(description);
        a.setSeverity(severity);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setEventId(eventId);
        a.setStatus(status == null ? "OPEN" : status);
        a.setAssignedTo(assignedTo);
        if (triggeredAt != null) {
            a.setTriggeredAt(triggeredAt);
        }
        return a;
    }
}