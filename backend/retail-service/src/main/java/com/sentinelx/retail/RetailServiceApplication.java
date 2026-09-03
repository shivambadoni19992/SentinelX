package com.sentinelx.retail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.sentinelx.retail.service.RetailProperties;

/**
 * SentinelX retail-service — Phase 1 scaffold. Health via Spring Boot Actuator.
 */
@SpringBootApplication
@EnableConfigurationProperties(RetailProperties.class)
public class RetailServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailServiceApplication.class, args);
    }
}
