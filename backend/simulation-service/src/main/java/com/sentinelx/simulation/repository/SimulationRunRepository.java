package com.sentinelx.simulation.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sentinelx.simulation.entity.SimulationRun;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {

    List<SimulationRun> findByStatus(String status);
}