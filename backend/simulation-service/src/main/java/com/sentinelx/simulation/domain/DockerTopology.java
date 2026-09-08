package com.sentinelx.simulation.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Docker Compose topology the network simulations are allowed to target.
 * This is the safety boundary for NETWORK_* drills:
 *
 * <ul>
 *   <li>Network simulations only ever <b>emit synthetic JSON events</b> onto
 *       the platform's own Kafka topics — no sockets are ever opened and no
 *       real traffic is generated.</li>
 *   <li>Every named target must resolve to a container in this compose
 *       topology; unknown names and external hosts are rejected.</li>
 *   <li>Destination IPs are always the compose-internal addresses below
 *       (RFC 1918 {@code 10.0.x.x}); source IPs use the documentation-reserved
 *       TEST-NET-3 range ({@code 203.0.113.x}) so nothing can be routed
 *       off-host even by accident.</li>
 * </ul>
 */
public final class DockerTopology {

    private DockerTopology() {
    }

    /** One targetable compose container: service name, internal IP, exposed ports. */
    public record Container(String service, String containerName, String internalIp,
                            List<Integer> ports) {
    }

    /** The compose-internal subnet prefix used for synthetic destination IPs. */
    public static final String INTERNAL_SUBNET = "10.0.";

    /** All targetable containers, mirroring docker-compose.yml. */
    private static final Map<String, Container> CONTAINERS = new LinkedHashMap<>();

    static {
        register("api-gateway", "sentinelx-api-gateway", "10.0.0.10", 8080);
        register("auth-service", "sentinelx-auth-service", "10.0.0.11", 8081);
        register("payment-service", "sentinelx-payment-service", "10.0.0.12", 8082);
        register("retail-service", "sentinelx-retail-service", "10.0.0.13", 8083);
        register("security-event-service", "sentinelx-security-event-service", "10.0.0.14", 8084);
        register("detection-engine", "sentinelx-detection-engine", "10.0.0.15", 8085);
        register("risk-engine", "sentinelx-risk-engine", "10.0.0.16", 8086);
        register("alert-service", "sentinelx-alert-service", "10.0.0.17", 8087);
        register("simulation-service", "sentinelx-simulation-service", "10.0.0.18", 8088);
        register("postgres", "sentinelx-postgres", "10.0.0.20", 5432);
        register("redis", "sentinelx-redis", "10.0.0.21", 6379);
        register("kafka", "sentinelx-kafka", "10.0.0.22", 9092);
        register("opensearch", "sentinelx-opensearch", "10.0.0.23", 9200);
        register("prometheus", "sentinelx-prometheus", "10.0.0.24", 9090);
        register("grafana", "sentinelx-grafana", "10.0.0.25", 3000);
        register("frontend", "sentinelx-frontend", "10.0.0.26", 8090);
    }

    private static void register(String service, String containerName, String ip, int... ports) {
        CONTAINERS.put(service, new Container(service, containerName, ip,
                java.util.Arrays.stream(ports).boxed().toList()));
    }

    /** All targetable service names, in stable order. */
    public static Set<String> services() {
        return java.util.Collections.unmodifiableSet(CONTAINERS.keySet());
    }

    /**
     * Resolves a target by compose service name or container name. Throws
     * {@code IllegalArgumentException} for anything that is not part of the
     * compose topology — external hosts can never be targeted.
     */
    public static Container resolve(String nameOrService) {
        if (nameOrService == null || nameOrService.isBlank()) {
            throw new IllegalArgumentException("target container is required");
        }
        String key = nameOrService.trim().toLowerCase();
        Container c = CONTAINERS.get(key);
        if (c != null) {
            return c;
        }
        for (Container candidate : CONTAINERS.values()) {
            if (candidate.containerName().equalsIgnoreCase(key)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown container '" + nameOrService
                + "' — network simulations may only target Docker Compose containers: "
                + String.join(", ", CONTAINERS.keySet()));
    }

    /**
     * Whether an IP is safe to use as a synthetic destination: only the
     * compose-internal {@code 10.0.x.x} range is allowed.
     */
    public static boolean isInternalDestinationIp(String ip) {
        return ip != null && ip.startsWith(INTERNAL_SUBNET);
    }
}