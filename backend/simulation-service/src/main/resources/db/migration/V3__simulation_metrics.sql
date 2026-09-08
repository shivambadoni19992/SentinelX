-- SentinelX simulation-service · per-scenario live metrics for the SOC dashboard.
ALTER TABLE simulator.simulation_runs
    ADD COLUMN IF NOT EXISTS metrics jsonb;