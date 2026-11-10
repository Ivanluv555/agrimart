ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN payment_no VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN payment_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_orders_reconcile ON orders(payment_no,payment_version,id);
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
