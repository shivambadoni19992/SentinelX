package com.sentinelx.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sentinelx.risk.model.RiskLevel;

/** Band mapping: 0–30 LOW, 31–60 MEDIUM, 61–80 HIGH, 81–100 CRITICAL. */
class RiskLevelTest {

    @Test
    void lowBand() {
        assertThat(RiskLevel.forScore(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.forScore(15)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.forScore(30)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mediumBand() {
        assertThat(RiskLevel.forScore(31)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.forScore(45)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.forScore(60)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void highBand() {
        assertThat(RiskLevel.forScore(61)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.forScore(75)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.forScore(80)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void criticalBand() {
        assertThat(RiskLevel.forScore(81)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(RiskLevel.forScore(100)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void clampsOutOfRangeScores() {
        assertThat(RiskLevel.forScore(-5)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.forScore(250)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void defaultActionsPerLevel() {
        assertThat(RiskLevel.LOW.defaultAction()).isEqualTo("ALLOW");
        assertThat(RiskLevel.MEDIUM.defaultAction()).isEqualTo("REVIEW");
        assertThat(RiskLevel.HIGH.defaultAction()).isEqualTo("CHALLENGE");
        assertThat(RiskLevel.CRITICAL.defaultAction()).isEqualTo("BLOCK");
    }
}