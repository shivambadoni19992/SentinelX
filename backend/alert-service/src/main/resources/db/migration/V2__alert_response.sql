-- SentinelX alert-service · response actions (schema: alerts)
ALTER TABLE alerts.security_alerts
    ADD COLUMN IF NOT EXISTS action        varchar(64),
    ADD COLUMN IF NOT EXISTS actor         varchar(128),
    ADD COLUMN IF NOT EXISTS action_detail jsonb;