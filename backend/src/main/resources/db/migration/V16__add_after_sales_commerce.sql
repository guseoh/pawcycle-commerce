CREATE TABLE deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    carrier_code VARCHAR(50) NULL,
    tracking_number VARCHAR(100) NULL,
    failure_reason VARCHAR(500) NULL,
    shipped_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    CONSTRAINT pk_deliveries PRIMARY KEY (id),
    CONSTRAINT uk_deliveries_order UNIQUE (order_id),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_deliveries_status CHECK (status IN ('PREPARING','SHIPPED','DELIVERED','FAILED','CANCELLED')),
    CONSTRAINT chk_deliveries_state CHECK (
        (status='PREPARING' AND shipped_at IS NULL AND delivered_at IS NULL AND failed_at IS NULL AND cancelled_at IS NULL)
        OR (status='SHIPPED' AND shipped_at IS NOT NULL AND delivered_at IS NULL AND failed_at IS NULL AND cancelled_at IS NULL)
        OR (status='DELIVERED' AND shipped_at IS NOT NULL AND delivered_at IS NOT NULL AND failed_at IS NULL AND cancelled_at IS NULL)
        OR (status='FAILED' AND shipped_at IS NOT NULL AND delivered_at IS NULL AND failed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status='CANCELLED' AND shipped_at IS NULL AND delivered_at IS NULL AND failed_at IS NULL AND cancelled_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_cancellations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_order_cancellations PRIMARY KEY (id),
    CONSTRAINT uk_order_cancellations_order UNIQUE (order_id),
    CONSTRAINT fk_order_cancellations_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_order_cancellations_status CHECK (status IN ('REFUND_PENDING','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_returns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    rejection_reason VARCHAR(500) NULL,
    restock BOOLEAN NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    received_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    decided_by_admin_id BIGINT NULL,
    received_by_admin_id BIGINT NULL,
    CONSTRAINT pk_order_returns PRIMARY KEY (id),
    CONSTRAINT uk_order_returns_order UNIQUE (order_id),
    CONSTRAINT fk_order_returns_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_returns_decided_admin FOREIGN KEY (decided_by_admin_id) REFERENCES members (id),
    CONSTRAINT fk_order_returns_received_admin FOREIGN KEY (received_by_admin_id) REFERENCES members (id),
    CONSTRAINT chk_order_returns_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','REFUND_PENDING','COMPLETED')),
    CONSTRAINT chk_order_returns_decision CHECK ((status IN ('REQUESTED') AND decided_at IS NULL) OR (status IN ('APPROVED','REJECTED','REFUND_PENDING','COMPLETED') AND decided_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    cancellation_id BIGINT NULL,
    return_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no INT NOT NULL,
    reconciliation_attempts INT NOT NULL DEFAULT 0,
    provider_status VARCHAR(100) NULL,
    failure_code VARCHAR(100) NULL,
    requested_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    last_reconciled_at DATETIME(6) NULL,
    source_id BIGINT GENERATED ALWAYS AS (COALESCE(cancellation_id,return_id)) STORED,
    succeeded_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='SUCCEEDED' THEN order_id ELSE NULL END) STORED,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT uk_refunds_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_refunds_source_attempt UNIQUE (source,source_id,attempt_no),
    CONSTRAINT uk_refunds_succeeded_order UNIQUE (succeeded_order_id),
    CONSTRAINT fk_refunds_cancellation FOREIGN KEY (cancellation_id) REFERENCES order_cancellations (id),
    CONSTRAINT fk_refunds_return FOREIGN KEY (return_id) REFERENCES order_returns (id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_refunds_source CHECK ((source='CANCELLATION' AND cancellation_id IS NOT NULL AND return_id IS NULL) OR (source='RETURN' AND return_id IS NOT NULL AND cancellation_id IS NULL)),
    CONSTRAINT chk_refunds_status CHECK (status IN ('READY','PROCESSING','SUCCEEDED','FAILED','UNKNOWN')),
    CONSTRAINT chk_refunds_provider CHECK (provider='TOSS'),
    CONSTRAINT chk_refunds_amount CHECK (amount > 0),
    CONSTRAINT chk_refunds_attempt CHECK (attempt_no BETWEEN 1 AND 3),
    CONSTRAINT chk_refunds_reconciliation_attempts CHECK (reconciliation_attempts BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE inventory_movements
    ADD COLUMN cancellation_id BIGINT NULL,
    ADD COLUMN return_id BIGINT NULL,
    ADD COLUMN source_id BIGINT NULL,
    ADD CONSTRAINT fk_inventory_movements_cancellation FOREIGN KEY (cancellation_id) REFERENCES order_cancellations (id),
    ADD CONSTRAINT fk_inventory_movements_return FOREIGN KEY (return_id) REFERENCES order_returns (id),
    DROP CONSTRAINT chk_inventory_movements_type,
    ADD CONSTRAINT chk_inventory_movements_type CHECK (type IN ('INBOUND','ADMIN_ADJUST','RESERVE','RELEASE','DEDUCT','CANCEL_RESTORE','RETURN_RESTORE')),
    ADD CONSTRAINT chk_inventory_movements_restore CHECK (
        (type='CANCEL_RESTORE' AND payment_id IS NULL AND cancellation_id IS NOT NULL AND return_id IS NULL AND source_id=cancellation_id)
        OR (type='RETURN_RESTORE' AND payment_id IS NULL AND return_id IS NOT NULL AND cancellation_id IS NULL AND source_id=return_id)
        OR (type NOT IN ('CANCEL_RESTORE','RETURN_RESTORE') AND cancellation_id IS NULL AND return_id IS NULL)
    );
UPDATE inventory_movements
SET source_id=payment_id
WHERE payment_id IS NOT NULL;
ALTER TABLE inventory_movements ADD CONSTRAINT uk_inventory_movements_source_type_sku UNIQUE (source_id,type,sku_id);

ALTER TABLE member_memberships MODIFY COLUMN evaluated_purchase_amount DECIMAL(18,2) NOT NULL;
ALTER TABLE membership_histories MODIFY COLUMN evaluated_purchase_amount DECIMAL(18,2) NOT NULL;
