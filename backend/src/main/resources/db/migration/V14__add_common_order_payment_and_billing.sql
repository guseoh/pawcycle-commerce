CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    original_amount DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL,
    shipping_fee DECIMAL(18,2) NOT NULL,
    payment_amount DECIMAL(18,2) NOT NULL,
    recipient_name VARCHAR(100) NULL,
    recipient_phone VARCHAR(30) NULL,
    postal_code VARCHAR(20) NULL,
    address_line1 VARCHAR(255) NULL,
    address_line2 VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_orders_source CHECK (source IN ('ONE_TIME','SUBSCRIPTION')),
    CONSTRAINT chk_orders_status CHECK (status IN ('CREATED','PAYMENT_PENDING','PAID','PAYMENT_FAILED','EXPIRED','PAYMENT_ACTION_REQUIRED')),
    CONSTRAINT chk_orders_amounts CHECK (original_amount >= 0 AND discount_amount >= 0 AND shipping_fee >= 0 AND payment_amount >= 0 AND payment_amount = original_amount - discount_amount + shipping_fee)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    snapshot_quality VARCHAR(30) NOT NULL,
    sku_code_snapshot VARCHAR(100) NULL,
    product_name_snapshot VARCHAR(200) NULL,
    sku_name_snapshot VARCHAR(200) NULL,
    unit_price DECIMAL(18,2) NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(18,2) NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uk_order_items_sku UNIQUE (order_id, sku_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_order_items_snapshot CHECK (snapshot_quality IN ('FULL','LEGACY_PARTIAL')),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_full CHECK ((snapshot_quality = 'LEGACY_PARTIAL') OR (sku_code_snapshot IS NOT NULL AND product_name_snapshot IS NOT NULL AND sku_name_snapshot IS NOT NULL AND unit_price IS NOT NULL AND line_amount IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subscription_order_context (
    order_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    effective_snapshot_id BIGINT NULL,
    source_plan_version_id BIGINT NULL,
    scheduled_date DATE NOT NULL,
    CONSTRAINT pk_subscription_order_context PRIMARY KEY (order_id),
    CONSTRAINT uk_subscription_order_context_schedule UNIQUE (schedule_id),
    CONSTRAINT fk_subscription_order_context_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_subscription_order_context_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_order_context_schedule FOREIGN KEY (schedule_id) REFERENCES subscription_schedules (id),
    CONSTRAINT fk_subscription_order_context_snapshot FOREIGN KEY (effective_snapshot_id) REFERENCES subscription_snapshots (id),
    CONSTRAINT fk_subscription_order_context_plan FOREIGN KEY (source_plan_version_id) REFERENCES plan_versions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_status VARCHAR(100) NULL,
    amount DECIMAL(18,2) NOT NULL,
    provider_order_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payment_key VARCHAR(200) NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no INT NOT NULL,
    failure_code VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    reconciliation_attempts INT NOT NULL DEFAULT 0,
    last_reconciled_at DATETIME(6) NULL,
    succeeded_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'SUCCEEDED' THEN order_id ELSE NULL END) STORED,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_provider_order UNIQUE (provider_order_id),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_order_attempt UNIQUE (order_id, attempt_no),
    CONSTRAINT uk_payments_successful_order UNIQUE (succeeded_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_payments_type CHECK (type IN ('NORMAL','BILLING')),
    CONSTRAINT chk_payments_provider CHECK (provider = 'TOSS'),
    CONSTRAINT chk_payments_status CHECK (status IN ('READY','PROCESSING','SUCCEEDED','FAILED','UNKNOWN')),
    CONSTRAINT chk_payments_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_payments_reconciliation_attempts CHECK (reconciliation_attempts BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE checkout_idempotency_results (
    member_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_checkout_idempotency_results PRIMARY KEY (member_id, idempotency_key),
    CONSTRAINT fk_checkout_idempotency_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_checkout_idempotency_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_checkout_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    payment_id BIGINT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    available_before INT NOT NULL,
    available_after INT NOT NULL,
    reserved_before INT NOT NULL,
    reserved_after INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT uk_inventory_movements_payment_type_sku UNIQUE (payment_id, type, sku_id),
    CONSTRAINT fk_inventory_movements_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT fk_inventory_movements_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_inventory_movements_type CHECK (type IN ('INBOUND','ADMIN_ADJUST','RESERVE','RELEASE','DEDUCT')),
    CONSTRAINT chk_inventory_movements_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_payment_methods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    customer_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    billing_key VARCHAR(300) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN member_id ELSE NULL END) STORED,
    CONSTRAINT pk_billing_payment_methods PRIMARY KEY (id),
    CONSTRAINT uk_billing_payment_methods_customer UNIQUE (customer_key),
    CONSTRAINT uk_billing_payment_methods_active_member UNIQUE (active_member_id),
    CONSTRAINT fk_billing_payment_methods_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_billing_payment_methods_provider CHECK (provider = 'TOSS'),
    CONSTRAINT chk_billing_payment_methods_status CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE billing_payment_method_preparations (
    prepare_token VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_id BIGINT NOT NULL,
    customer_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_billing_payment_method_preparations PRIMARY KEY (prepare_token),
    CONSTRAINT fk_billing_preparations_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subscription_shipping_snapshots (
    subscription_id BIGINT NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_subscription_shipping_snapshots PRIMARY KEY (subscription_id),
    CONSTRAINT fk_subscription_shipping_snapshots_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE subscription_schedules
    ADD COLUMN hold_reason VARCHAR(40) NULL,
    ADD CONSTRAINT chk_subscription_schedules_hold_reason CHECK (hold_reason IS NULL OR hold_reason IN ('MISSING_SHIPPING_ADDRESS','MISSING_BILLING_METHOD','PAYMENT_RETRY_EXHAUSTED'));

ALTER TABLE member_coupons
    ADD CONSTRAINT fk_member_coupons_reserved_order FOREIGN KEY (reserved_order_id) REFERENCES orders (id);
