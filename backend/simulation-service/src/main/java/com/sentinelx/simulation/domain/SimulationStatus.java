package com.sentinelx.simulation.domain;

/**
 * Lifecycle of a simulation run: QUEUED → RUNNING → COMPLETED,
 * with FAILED and CANCELLED as terminal error paths.
 */
public enum SimulationStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** Terminal — no further state transitions allowed. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
