package com.sentinelx.simulation.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common simulation parameters. Nulls fall back to sensible defaults and
 * {@link #validate(SimulationLimits.Limits)} enforces the platform's safe
 * upper limits — an over-limit request is rejected, never silently clamped.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationConfig(
        @JsonProperty("numberOfUsers") Integer numberOfUsers,
        @JsonProperty("numberOfDevices") Integer numberOfDevices,
        @JsonProperty("numberOfIpAddresses") Integer numberOfIpAddresses,
        @JsonProperty("durationSeconds") Integer durationSeconds,
        @JsonProperty("eventsPerSecond") Integer eventsPerSecond,
        @JsonProperty("attackPercentage") Integer attackPercentage,
        @JsonProperty("intensity") Integer intensity) {

    public static SimulationConfig defaults() {
        return new SimulationConfig(100, 100, 50, 60, 10, 25, 50);
    }

    public SimulationConfig {
        numberOfUsers = numberOfUsers == null ? 100 : numberOfUsers;
        numberOfDevices = numberOfDevices == null ? 100 : numberOfDevices;
        numberOfIpAddresses = numberOfIpAddresses == null ? 50 : numberOfIpAddresses;
        durationSeconds = durationSeconds == null ? 60 : durationSeconds;
        eventsPerSecond = eventsPerSecond == null ? 10 : eventsPerSecond;
        attackPercentage = attackPercentage == null ? 25 : attackPercentage;
        intensity = intensity == null ? 50 : intensity;
    }

    /** Rejects any value outside the safe envelope with a descriptive message. */
    public void validate(SimulationLimits.Limits limits) {
        requireRange("numberOfUsers", numberOfUsers, 1, limits.maxUsers());
        requireRange("numberOfDevices", numberOfDevices, 1, limits.maxDevices());
        requireRange("numberOfIpAddresses", numberOfIpAddresses, 1, limits.maxIpAddresses());
        requireRange("durationSeconds", durationSeconds, 1, limits.maxDurationSeconds());
        requireRange("eventsPerSecond", eventsPerSecond, 1, limits.maxEventsPerSecond());
        requireRange("attackPercentage", attackPercentage, 0, 100);
        requireRange("intensity", intensity, 0, 100);
        long totalEvents = (long) durationSeconds * eventsPerSecond;
        if (totalEvents > limits.maxTotalEvents()) {
            throw new IllegalArgumentException(
                    "total events (" + totalEvents + " = durationSeconds x eventsPerSecond) exceeds the safe limit of "
                            + limits.maxTotalEvents());
        }
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    /** JSON-friendly map for persistence in the config column. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("numberOfUsers", numberOfUsers);
        map.put("numberOfDevices", numberOfDevices);
        map.put("numberOfIpAddresses", numberOfIpAddresses);
        map.put("durationSeconds", durationSeconds);
        map.put("eventsPerSecond", eventsPerSecond);
        map.put("attackPercentage", attackPercentage);
        map.put("intensity", intensity);
        return map;
    }
}
