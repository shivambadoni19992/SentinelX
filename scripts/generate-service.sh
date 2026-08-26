#!/usr/bin/env bash
# Scaffolds minimal Spring Boot 3 / Java 21 microservices for SentinelX.
# Phase 1: runnable shells only (health endpoint + actuator + structured logging).
# Usage: ./scripts/generate-service.sh <service-id>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/backend"

declare -A PKG=(
  [api-gateway]=gateway
  [auth-service]=auth
  [payment-service]=payment
  [retail-service]=retail
  [security-event-service]=securityevent
  [detection-engine]=detection
  [risk-engine]=risk
  [alert-service]=alert
  [simulation-service]=simulation
)

declare -A CLASS=(
  [api-gateway]=ApiGatewayApplication
  [auth-service]=AuthServiceApplication
  [payment-service]=PaymentServiceApplication
  [retail-service]=RetailServiceApplication
  [security-event-service]=SecurityEventServiceApplication
  [detection-engine]=DetectionEngineApplication
  [risk-engine]=RiskEngineApplication
  [alert-service]=AlertServiceApplication
  [simulation-service]=SimulationServiceApplication
)

declare -A PORT=(
  [api-gateway]=8080
  [auth-service]=8081
  [payment-service]=8082
  [retail-service]=8083
  [security-event-service]=8084
  [detection-engine]=8085
  [risk-engine]=8086
  [alert-service]=8087
  [simulation-service]=8088
)

ID="${1:?usage: generate-service.sh <service-id>}"
PKGN="${PKG[$ID]:-}"
CLS="${CLASS[$ID]:-}"
PRT="${PORT[$ID]:-}"

if [[ -z "$PKGN" || -z "$CLS" || -z "$PRT" ]]; then
  echo "Unknown service: $ID" >&2
  exit 1
fi

DIR="$BACKEND/$ID"
mkdir -p "$DIR/src/main/java/com/sentinelx/$PKGN" "$DIR/src/main/resources"

if [[ "$ID" == "api-gateway" ]]; then
  WEB_DEP='<dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>'
else
  WEB_DEP='<dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>'
fi

cat > "$DIR/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.sentinelx</groupId>
        <artifactId>sentinelx-backend</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>sentinelx-$ID</artifactId>
    <name>SentinelX $ID</name>
    <description>Minimal Phase 1 scaffold for $ID</description>
    <dependencies>
$WEB_DEP
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
POM

cat > "$DIR/src/main/java/com/sentinelx/$PKGN/$CLS.java" <<JAVA
package com.sentinelx.$PKGN;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SentinelX $ID — Phase 1 scaffold. Health via Spring Boot Actuator.
 */
@SpringBootApplication
public class $CLS {

    public static void main(String[] args) {
        SpringApplication.run($CLS.class, args);
    }
}
JAVA
cat > "$DIR/src/main/resources/application.yml" <<YML
server:
  port: $PRT

spring:
  application:
    name: sentinelx-$ID
  profiles:
    active: \${SPRING_PROFILES_ACTIVE:default}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always

info:
  service: sentinelx-$ID
  env: \${SENTINELX_ENV:local}

logging:
  level:
    root: INFO
YML

if [[ "$ID" == "api-gateway" ]]; then
cat >> "$DIR/src/main/resources/application.yml" <<'GW'
---
spring:
  config:
    activate:
      on-profile: default
  cloud:
    gateway:
      routes:
        - id: auth
          uri: http://auth-service:8081
          predicates: [ Path=/api/auth/** ]
        - id: payment
          uri: http://payment-service:8082
          predicates: [ Path=/api/payments/** ]
        - id: retail
          uri: http://retail-service:8083
          predicates: [ Path=/api/retail/** ]
        - id: events
          uri: http://security-event-service:8084
          predicates: [ Path=/api/events/** ]
        - id: detection
          uri: http://detection-engine:8085
          predicates: [ Path=/api/detections/** ]
        - id: risk
          uri: http://risk-engine:8086
          predicates: [ Path=/api/risk/** ]
        - id: alerts
          uri: http://alert-service:8087
          predicates: [ Path=/api/alerts/** ]
        - id: simulation
          uri: http://simulation-service:8088
          predicates:
            - Path=/api/simulations/**
            - Path=/api/ssr/**
GW
fi

# Minimal delimited structured logging (no extra dependencies).
cat > "$DIR/src/main/resources/logback-spring.xml" <<'LBCK'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <springProperty scope="context" name="SERVICE" source="spring.application.name" defaultValue="sentinelx"/>
    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}|%level|${SERVICE}|%thread|%logger{40}|%replace(%msg){'\\n',' '}%n"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>utf8</charset>
        </encoder>
    </appender>
    <logger name="org.apache" level="WARN"/>
    <logger name="reactor" level="WARN"/>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
LBCK

echo "scaffolded $ID -> $DIR"