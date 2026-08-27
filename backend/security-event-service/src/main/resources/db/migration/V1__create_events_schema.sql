-- SentinelX security-event-service · schema: events
CREATE SCHEMA IF NOT EXISTS events;

CREATE TABLE events.security_events (
    id          uuid         PRIMARY KEY,
    event_type  varchar(255) NOT NULL,
    user_id     uuid,
    device_id   uuid,
    session_id  uuid,
    actor       text,
    action      varchar(255),
    outcome     varchar(64)  NOT NULL DEFAULT 'UNKNOWN',
    severity    varchar(64)  NOT NULL DEFAULT 'LOW',
    source_ip   inet,
    metadata    jsonb,
    occurred_at timestamptz  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_security_events_event_type ON events.security_events (event_type);
CREATE INDEX idx_security_events_user_id    ON events.security_events (user_id);
CREATE INDEX idx_security_events_severity   ON events.security_events (severity);
CREATE INDEX idx_security_events_occurred   ON events.security_events (occurred_at);

CREATE TABLE events.audit_logs (
    id            uuid         PRIMARY KEY,
    user_id       uuid,
    action        varchar(255) NOT NULL,
    actor         text,
    resource_type varchar(128),
    resource_id   uuid,
    result        varchar(64),
    details       jsonb,
    occurred_at   timestamptz  NOT NULL DEFAULT now(),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user_id       ON events.audit_logs (user_id);
CREATE INDEX idx_audit_logs_resource_type ON events.audit_logs (resource_type);
CREATE INDEX idx_audit_logs_occurred_at   ON events.audit_logs (occurred_at);