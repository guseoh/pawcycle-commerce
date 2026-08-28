CREATE TABLE subscription_schedule_addons (
    schedule_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_krw DECIMAL(18,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_subscription_schedule_addons PRIMARY KEY (schedule_id, sku_id),
    CONSTRAINT fk_subscription_schedule_addons_schedule FOREIGN KEY (schedule_id) REFERENCES subscription_schedules (id),
    CONSTRAINT fk_subscription_schedule_addons_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_subscription_schedule_addons_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT chk_subscription_schedule_addons_price CHECK (unit_price_krw >= 0),
    INDEX ix_subscription_schedule_addons_sku (sku_id, schedule_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subscription_order_addon_items (
    subscription_order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_krw DECIMAL(18,2) NOT NULL,
    CONSTRAINT pk_subscription_order_addon_items PRIMARY KEY (subscription_order_id, sku_id),
    CONSTRAINT fk_subscription_order_addon_items_order FOREIGN KEY (subscription_order_id) REFERENCES subscription_orders (id),
    CONSTRAINT fk_subscription_order_addon_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_subscription_order_addon_items_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT chk_subscription_order_addon_items_price CHECK (unit_price_krw >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE subscription_schedules
    DROP CONSTRAINT chk_subscription_schedules_hold_reason,
    ADD CONSTRAINT chk_subscription_schedules_hold_reason CHECK (
        hold_reason IS NULL OR hold_reason IN (
            'MISSING_SHIPPING_ADDRESS',
            'MISSING_BILLING_METHOD',
            'PAYMENT_RETRY_EXHAUSTED',
            'PAYMENT_RETRY_STOCK_UNAVAILABLE',
            'ORDER_STOCK_UNAVAILABLE'
        )
    );

ALTER TABLE subscription_orders
    MODIFY package_total_krw DECIMAL(18,2) NOT NULL;
