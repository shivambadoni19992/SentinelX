-- SentinelX auth-service · schema: whoami
-- Users, Devices and Sessions. UUID primary keys throughout.
CREATE SCHEMA IF NOT EXISTS whoami;

CREATE TABLE whoami.users (
    id             uuid         PRIMARY KEY,
    username       varchar(64)  NOT NULL,
    email          varchar(255) NOT NULL,
    password_hash  varchar(255) NOT NULL,
    role           varchar(64)  NOT NULL DEFAULT 'CUSTOMER',
    account_status varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_username ON whoami.users (username);
CREATE UNIQUE INDEX uq_users_email    ON whoami.users (email);
CREATE INDEX        idx_users_role    ON whoami.users (role);

CREATE TABLE whoami.devices (
    id                 uuid         PRIMARY KEY,
    user_id            uuid         NOT NULL REFERENCES whoami.users (id) ON DELETE CASCADE,
    device_fingerprint varchar(255) NOT NULL,
    device_type        varchar(64),
    ip_address         inet,
    user_agent         text,
    trusted            boolean      NOT NULL DEFAULT false,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_devices_user_fingerprint ON whoami.devices (user_id, device_fingerprint);
CREATE INDEX        idx_devices_ip_address      ON whoami.devices (ip_address);

CREATE TABLE whoami.sessions (
    id           uuid         PRIMARY KEY,
    user_id      uuid         NOT NULL REFERENCES whoami.users (id) ON DELETE CASCADE,
    device_id    uuid         REFERENCES whoami.devices (id) ON DELETE SET NULL,
    token_hash   varchar(255) NOT NULL,
    ip_address   inet,
    user_agent   text,
    status       varchar(64)  NOT NULL DEFAULT 'ACTIVE',
    started_at   timestamptz  NOT NULL DEFAULT now(),
    last_seen_at timestamptz,
    expires_at   timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_sessions_token_hash  ON whoami.sessions (token_hash);
CREATE INDEX        idx_sessions_user_id    ON whoami.sessions (user_id);
CREATE INDEX        idx_sessions_expires_at ON whoami.sessions (expires_at);