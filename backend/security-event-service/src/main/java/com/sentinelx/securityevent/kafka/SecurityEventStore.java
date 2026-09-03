package com.sentinelx.securityevent.kafka;

import com.sentinelx.securityevent.entity.SecurityEvent;
import com.sentinelx.securityevent.repository.SecurityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence port for normalized events. Kept separate from the listener so
 * tests can substitute an in-memory implementation.
 */
@Component
public class SecurityEventStore {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventStore.class);

    private final SecurityEventRepository repository;

    public SecurityEventStore(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void persist(SecurityEvent event) {
        repository.save(event);
        if (log.isDebugEnabled()) {
            log.debug("persisted security event id={} type={}", event.getId(), event.getEventType());
        }
    }
}
