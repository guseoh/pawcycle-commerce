CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    delivery_cycle_weeks INT NOT NULL,
    created_date DATE NOT NULL,
    next_order_date DATE NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_subscriptions_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_subscriptions_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT chk_subscriptions_delivery_cycle CHECK (delivery_cycle_weeks IN (2, 4, 8)),
    CONSTRAINT chk_subscriptions_date_order
        CHECK (next_order_date = DATE_ADD(created_date, INTERVAL delivery_cycle_weeks WEEK))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_subscriptions_member_id
    ON subscriptions (member_id, id);
