package com.sentinelx.simulation.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Safe upper limits for simulations. Simulations only inject synthetic events
 * into the platform's own Kafka topics, but the volume must stay bounded so a
 * misconfigured run cannot flood the pipeline.
 */
@ConfigurationProperties(prefix = "sentinelx.simulation")
public record SimulationLimits(
        int maxConcurrentRuns,
        Limits limits) {

    public record Limits(
            int maxUsers,
            int maxDevices,
            int maxIpAddresses,
            int maxDurationSeconds,
            int maxEventsPerSecond,
            long maxTotalEvents) {

        public Limits {
            if (maxUsers <= 0) maxUsers = 10_000;
            if (maxDevices <= 0) maxDevices = 10_000;
            if (maxIpAddresses <= 0) maxIpAddresses = 10_000;
            if (maxDurationSeconds <= 0) maxDurationSeconds = 600;
            if (maxEventsPerSecond <= 0) maxEventsPerSecond = 1_000;
            if (maxTotalEvents <= 0) maxTotalEvents = 50_000;
        }
    }

    public SimulationLimits {
        if (maxConcurrentRuns <= 0) maxConcurrentRuns = 4;
        if (limits == null) limits = new Limits(0, 0, 0, 0, 0, 0);
    }
}
