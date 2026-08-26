package com.sentinelx.gateway;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Minimal infrastructure/diagnostics aggregator. Returns the live health of
 * each SentinelX microservice by probing its Spring Boot Actuator endpoint.
 * This is operational wiring, not business logic.
 */
@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    private record Target(String id, String name, String url) {
    }

    private static final Target[] TARGETS = {
        new Target("api-gateway", "API Gateway", "http://localhost:8080"),
        new Target("auth-service", "Auth Service", "http://auth-service:8081"),
        new Target("payment-service", "Payment Service", "http://payment-service:8082"),
        new Target("retail-service", "Retail Service", "http://retail-service:8083"),
        new Target("security-event-service", "Security Event Service", "http://security-event-service:8084"),
        new Target("detection-engine", "Detection Engine", "http://detection-engine:8085"),
        new Target("risk-engine", "Risk Engine", "http://risk-engine:8086"),
        new Target("alert-service", "Alert Service", "http://alert-service:8087"),
        new Target("simulation-service", "Simulation Service", "http://simulation-service:8088")
    };

    private final WebClient webClient;

    public SystemHealthController(WebClient.Builder builder) {
        this.webClient = builder
                .defaultHeader("User-Agent", "sentinelx-gateway")
                .build();
    }

    @GetMapping("/services")
    public Mono<Map<String, Object>> services() {
        return Flux.fromArray(TARGETS)
                .flatMap(this::probe, 16)
                .collectList()
                .map(list -> Map.of("revealed", true, "services", list, "count", list.size()));
    }

    private Mono<Map<String, Object>> probe(Target target) {
        return webClient.get()
                .uri(target.url() + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> status(target, "UP", body))
                .onErrorResume(err -> Mono.just(status(target, "DOWN", Map.of("error", err.getClass().getSimpleName()))));
    }

    private Map<String, Object> status(Target target, String status, Object details) {
        return new java.util.LinkedHashMap<>(Map.of(
                "id", target.id(),
                "name", target.name(),
                "url", target.url(),
                "status", status,
                "checkedAt", java.time.Instant.now().toString(),
                "details", details));
    }
}