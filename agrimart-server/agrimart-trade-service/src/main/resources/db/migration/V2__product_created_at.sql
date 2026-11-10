ALTER TABLE products
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE INDEX idx_products_status_created_id ON products (status, created_at, id);
