package com.sentinelx.simulation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DownstreamTrackerTest {

    @Test
    void autoActionForMapsRiskLevelToResponse() {
        assertThat(DownstreamTracker.autoActionFor("MEDIUM")).isEqualTo("RATE_LIMIT");
        assertThat(DownstreamTracker.autoActionFor("HIGH")).isEqualTo("BLOCK_ACCOUNT");
        assertThat(DownstreamTracker.autoActionFor("CRITICAL")).isEqualTo("BLOCK_ACCOUNT");
        assertThat(DownstreamTracker.autoActionFor("LOW")).isNull();
        assertThat(DownstreamTracker.autoActionFor(null)).isNull();
        assertThat(DownstreamTracker.autoActionFor("medium")).isEqualTo("RATE_LIMIT");
    }

    @Test
    void paramDoubleReadsNumericParamsWithFallback() {
        Map<String, Object> params = Map.of("attemptsPerSecond", 25);
        assertThat(SimulationRunner.paramDouble(params, "attemptsPerSecond", 10)).isEqualTo(25.0);
        assertThat(SimulationRunner.paramDouble(params, "missing", 10)).isEqualTo(10.0);
        assertThat(SimulationRunner.paramDouble(null, "attemptsPerSecond", 7)).isEqualTo(7.0);
    }
}