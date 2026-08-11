CREATE TABLE subscription_order_items (
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT pk_subscription_order_items PRIMARY KEY (order_id, sku_id),
    CONSTRAINT fk_subscription_order_items_order FOREIGN KEY (order_id) REFERENCES subscription_orders (id),
    CONSTRAINT fk_subscription_order_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_subscription_order_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
