ALTER TABLE payments ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN event_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE payments ADD COLUMN provider_transaction_no VARCHAR(255) NULL;
ALTER TABLE payments ADD COLUMN paid_at DATETIME(6) NULL;
CREATE TABLE fund_refunds (
    refund_no VARCHAR(64) PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    provider_reference VARCHAR(128) NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_refund_payment UNIQUE(payment_no)
);
CREATE TABLE payment_close_tombstones (
    payment_no VARCHAR(64) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL
);
