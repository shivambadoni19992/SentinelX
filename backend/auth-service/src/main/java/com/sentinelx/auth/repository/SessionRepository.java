package com.sentinelx.auth.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.auth.entity.Session;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findByUserId(UUID userId);
}