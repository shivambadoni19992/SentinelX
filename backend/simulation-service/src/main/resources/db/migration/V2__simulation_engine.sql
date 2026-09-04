-- SentinelX simulation-service · extend simulation_runs for the simulation engine.
ALTER TABLE simulator.simulation_runs
    ADD COLUMN IF NOT EXISTS type             varchar(64),
    ADD COLUMN IF NOT EXISTS events_generated bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS events_processed bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS detections       bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS risk_decisions   bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS alerts           bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS actions          bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS errors           jsonb  NOT NULL DEFAULT '[]'::jsonb;

-- Scenario was superseded by the typed SimulationType.
UPDATE simulator.simulation_runs SET type = scenario WHERE type IS NULL AND scenario IS NOT NULL;

-- New lifecycle statuses (QUEUED → RUNNING → COMPLETED / FAILED / CANCELLED).
UPDATE simulator.simulation_runs SET status = 'QUEUED' WHERE status = 'PENDING';
ALTER TABLE simulator.simulation_runs ALTER COLUMN status SET DEFAULT 'QUEUED';
