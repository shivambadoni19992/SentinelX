package com.sentinelx.securityevent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SentinelX security-event-service — Phase 1 scaffold. Health via Spring Boot Actuator.
 */
@SpringBootApplication
public class SecurityEventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityEventServiceApplication.class, args);
    }
}
