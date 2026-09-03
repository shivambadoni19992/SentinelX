-- SentinelX payment-service · synthetic payment processing (Phase 2)
-- Migrates the Phase-1 scaffold table to the payment-processing contract:
--   paymentId, customerId, merchantId, amount, currency, deviceId,
--   ipAddress, status(PENDING|APPROVED|HELD|DECLINED), createdAt
-- plus an idempotency key. Legacy scaffold-only columns are dropped so the
-- JPA entity (ddl-auto: validate) matches the entity exactly.

ALTER TABLE payments.payments RENAME COLUMN user_id TO customer_id;

ALTER TABLE payments.payments
    ADD COLUMN merchant_id     uuid,
    ADD COLUMN device_id       varchar(64),
    ADD COLUMN ip_address      varchar(45),
    ADD COLUMN idempotency_key varchar(128),
    ADD COLUMN decision_reason varchar(128);

ALTER TABLE payments.payments
    DROP COLUMN IF EXISTS order_id,
    DROP COLUMN IF EXISTS payment_method,
    DROP COLUMN IF EXISTS transaction_id,
    DROP COLUMN IF EXISTS risk_score,
    DROP COLUMN IF EXISTS failure_reason,
    DROP COLUMN IF EXISTS originated_at;

-- Indexes owned by dropped columns.
DROP INDEX IF EXISTS payments.uq_payments_transaction_id;
DROP INDEX IF EXISTS payments.idx_payments_order_id;

-- Strengthen the status domain to the processing contract.
ALTER TABLE payments.payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments.payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'HELD', 'DECLINED'));

-- Idempotency: at most one payment per client-supplied key.
CREATE UNIQUE INDEX uq_payments_idempotency_key ON payments.payments (idempotency_key);

-- Ownership-scoped reads are the hot path.
DROP INDEX IF EXISTS payments.idx_payments_user_id;
CREATE INDEX idx_payments_customer_id ON payments.payments (customer_id);
CREATE INDEX idx_payments_merchant_id ON payments.payments (merchant_id);