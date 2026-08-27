-- SentinelX alert-service · schema: alerts
CREATE SCHEMA IF NOT EXISTS alerts;

CREATE TABLE alerts.security_alerts (
    id           uuid         PRIMARY KEY,
    title        varchar(255) NOT NULL,
    description  text,
    severity     varchar(64)  NOT NULL,
    entity_type  varchar(64),
    entity_id    uuid,
    event_id     uuid,
    status       varchar(64)  NOT NULL DEFAULT 'OPEN',
    assigned_to  varchar(128),
    triggered_at timestamptz  NOT NULL DEFAULT now(),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_alerts_severity    ON alerts.security_alerts (severity);
CREATE INDEX idx_security_alerts_status      ON alerts.security_alerts (status);
CREATE INDEX idx_security_alerts_triggered   ON alerts.security_alerts (triggered_at);