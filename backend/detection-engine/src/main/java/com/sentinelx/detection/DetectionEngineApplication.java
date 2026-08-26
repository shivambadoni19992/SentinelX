package com.sentinelx.detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SentinelX detection-engine — Phase 1 scaffold. Health via Spring Boot Actuator.
 */
@SpringBootApplication
public class DetectionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DetectionEngineApplication.class, args);
    }
}
