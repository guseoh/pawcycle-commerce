ALTER TABLE carts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_carts_version_nonnegative CHECK (version >= 0);

ALTER TABLE checkout_idempotency_results
    ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

CREATE TABLE quick_reorder_idempotency_results (
    member_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_order_id BIGINT NOT NULL,
    response_json JSON NOT NULL,
    cart_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_quick_reorder_idempotency_results PRIMARY KEY (member_id, idempotency_key),
    CONSTRAINT fk_quick_reorder_idempotency_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_quick_reorder_idempotency_order FOREIGN KEY (source_order_id) REFERENCES orders (id),
    CONSTRAINT chk_quick_reorder_idempotency_cart_version CHECK (cart_version >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
