CREATE TABLE IF NOT EXISTS mq_outbox (
    event_id VARCHAR(100) PRIMARY KEY,
    routing_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(255) NULL
);
CREATE INDEX IF NOT EXISTS idx_mq_outbox_due ON mq_outbox(sent_at,next_attempt_at);
CREATE TABLE IF NOT EXISTS mq_inbox (
    consumer VARCHAR(64) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY(consumer,event_id)
);
CREATE TABLE IF NOT EXISTS mq_inbox_stream (
    stream_id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL
);
