-- SentinelX payment-service · schema: payments
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE payments.payments (
    id              uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL,
    order_id        uuid,
    amount          numeric(19,4)  NOT NULL CHECK (amount >= 0),
    currency        varchar(3)   NOT NULL DEFAULT 'USD',
    payment_method  varchar(64),
    transaction_id  varchar(255),
    status          varchar(64)  NOT NULL DEFAULT 'PENDING',
    risk_score      numeric(5,2),
    failure_reason  text,
    originated_at   timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_payments_transaction_id ON payments.payments (transaction_id);
CREATE INDEX        idx_payments_user_id       ON payments.payments (user_id);
CREATE INDEX        idx_payments_order_id      ON payments.payments (order_id);
CREATE INDEX        idx_payments_status        ON payments.payments (status);