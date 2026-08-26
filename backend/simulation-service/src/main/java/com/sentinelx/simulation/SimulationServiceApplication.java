package com.sentinelx.simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SentinelX simulation-service — Phase 1 scaffold. Health via Spring Boot Actuator.
 */
@SpringBootApplication
public class SimulationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulationServiceApplication.class, args);
    }
}
