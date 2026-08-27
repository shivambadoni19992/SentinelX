-- SentinelX simulation-service · schema: simulator
CREATE SCHEMA IF NOT EXISTS simulator;

CREATE TABLE simulator.simulation_runs (
    id           uuid         PRIMARY KEY,
    name         varchar(255) NOT NULL,
    description  text,
    scenario     varchar(128),
    config       jsonb,
    status       varchar(64)  NOT NULL DEFAULT 'PENDING',
    started_at   timestamptz,
    completed_at timestamptz,
    run_by       varchar(128),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_simulation_runs_status     ON simulator.simulation_runs (status);
CREATE INDEX idx_simulation_runs_started_at ON simulator.simulation_runs (started_at);