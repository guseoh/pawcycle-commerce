CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uk_notifications_event UNIQUE (member_id,type,reference_type,reference_id),
    CONSTRAINT fk_notifications_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_PAID','ORDER_SHIPPED','ORDER_DELIVERED','PAYMENT_ACTION_REQUIRED','CANCELLATION_COMPLETED','RETURN_APPROVED','RETURN_REJECTED','RETURN_COMPLETED','REFUND_ACTION_REQUIRED','SUBSCRIPTION_HELD')),
    INDEX ix_notifications_member_read_created (member_id,read_at,created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    safe_detail_json JSON NOT NULL,
    request_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_admin_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_logs_admin FOREIGN KEY (admin_id) REFERENCES members (id),
    INDEX ix_admin_audit_logs_created (created_at),
    INDEX ix_admin_audit_logs_target (target_type,target_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_payments_operations ON payments (status,created_at);
CREATE INDEX ix_refunds_operations ON refunds (status,requested_at);
CREATE INDEX ix_deliveries_operations ON deliveries (status,shipped_at);
CREATE INDEX ix_order_returns_operations ON order_returns (status,requested_at);
