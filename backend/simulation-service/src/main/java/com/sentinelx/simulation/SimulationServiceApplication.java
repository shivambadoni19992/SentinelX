package com.sentinelx.simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SentinelX simulation-service — generates realistic synthetic traffic that
 * flows through the real security pipeline (detection → risk → alert).
 */
@SpringBootApplication
@EnableScheduling
public class SimulationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulationServiceApplication.class, args);
    }
}
