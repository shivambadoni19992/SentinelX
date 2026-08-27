package com.sentinelx.alert.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.alert.entity.SecurityAlert;

public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, UUID> {

    List<SecurityAlert> findBySeverity(String severity);

    List<SecurityAlert> findByStatus(String status);
}