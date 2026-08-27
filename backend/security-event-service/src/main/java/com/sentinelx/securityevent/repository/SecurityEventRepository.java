package com.sentinelx.securityevent.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.securityevent.entity.SecurityEvent;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    List<SecurityEvent> findByEventType(String eventType);

    List<SecurityEvent> findByUserId(UUID userId);

    List<SecurityEvent> findByOccurredAtAfter(Instant after);
}