-- SentinelX risk-engine · schema: risk
CREATE SCHEMA IF NOT EXISTS risk;

CREATE TABLE risk.risk_decisions (
    id           uuid          PRIMARY KEY,
    subject_id   uuid          NOT NULL,
    subject_type varchar(64)   NOT NULL,
    rule_version varchar(64),
    risk_level   varchar(64)   NOT NULL DEFAULT 'LOW',
    risk_score   numeric(5,2)  NOT NULL CHECK (risk_score >= 0 AND risk_score <= 100),
    factors      jsonb,
    action       varchar(255)  NOT NULL DEFAULT 'ALLOW',
    decision_at  timestamptz,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_decisions_subject    ON risk.risk_decisions (subject_type, subject_id);
CREATE INDEX idx_risk_decisions_decision   ON risk.risk_decisions (decision_at);
CREATE INDEX idx_risk_decisions_risk_level ON risk.risk_decisions (risk_level);