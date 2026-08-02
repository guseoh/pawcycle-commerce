ALTER TABLE subscriptions
    ADD COLUMN pet_id BIGINT NULL,
    ADD COLUMN status VARCHAR(20) NULL,
    ADD COLUMN version BIGINT NULL,
    ADD COLUMN current_snapshot_id BIGINT NULL,
    ADD COLUMN legacy_api_visible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN mvp2_managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_status
        CHECK (status IS NULL OR status IN ('ACTIVE', 'PAUSED', 'CANCELED'));

CREATE TABLE pets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    pet_type VARCHAR(3) NOT NULL,
    CONSTRAINT pk_pets PRIMARY KEY (id),
    CONSTRAINT fk_pets_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_pets_type CHECK (pet_type IN ('DOG', 'CAT')),
    CONSTRAINT chk_pets_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 50)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_pets_member_id ON pets (member_id, id);

ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_pet FOREIGN KEY (pet_id) REFERENCES pets (id);

CREATE TABLE subscription_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NULL,
    target_pet_type VARCHAR(3) NOT NULL,
    on_sale BOOLEAN NOT NULL,
    sale_starts_on DATE NULL,
    sale_ends_on DATE NULL,
    current_plan_version_id BIGINT NULL,
    CONSTRAINT pk_subscription_plans PRIMARY KEY (id),
    CONSTRAINT chk_subscription_plans_type CHECK (target_pet_type IN ('DOG', 'CAT')),
    CONSTRAINT chk_subscription_plans_sales_dates CHECK (sale_ends_on IS NULL OR sale_starts_on IS NULL OR sale_starts_on <= sale_ends_on)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    package_price_krw BIGINT NOT NULL,
    is_migration_only BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_plan_versions PRIMARY KEY (id),
    CONSTRAINT fk_plan_versions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT chk_plan_versions_price CHECK (package_price_krw BETWEEN 0 AND 9007199254740991)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE plan_versions
    ADD CONSTRAINT uk_plan_versions_id_plan UNIQUE (id, plan_id);

ALTER TABLE subscription_plans
    ADD CONSTRAINT fk_subscription_plans_current_version
        FOREIGN KEY (current_plan_version_id, id) REFERENCES plan_versions (id, plan_id);

CREATE TABLE plan_items (
    plan_version_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT pk_plan_items PRIMARY KEY (plan_version_id, sku_id),
    CONSTRAINT fk_plan_items_version FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id),
    CONSTRAINT fk_plan_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_plan_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_version_delivery_cycles (
    plan_version_id BIGINT NOT NULL,
    delivery_cycle_weeks INT NOT NULL,
    CONSTRAINT pk_plan_version_delivery_cycles PRIMARY KEY (plan_version_id, delivery_cycle_weeks),
    CONSTRAINT fk_plan_cycles_version FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id),
    CONSTRAINT chk_plan_cycles_value CHECK (delivery_cycle_weeks IN (2, 4, 8))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NULL,
    source_plan_version_id BIGINT NOT NULL,
    package_total_krw BIGINT NOT NULL,
    delivery_cycle_weeks INT NOT NULL,
    CONSTRAINT pk_subscription_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_subscription_snapshots_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_snapshots_plan_version FOREIGN KEY (source_plan_version_id) REFERENCES plan_versions (id),
    CONSTRAINT chk_subscription_snapshots_total CHECK (package_total_krw BETWEEN 0 AND 9007199254740991),
    CONSTRAINT chk_subscription_snapshots_cycle CHECK (delivery_cycle_weeks IN (2, 4, 8))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscriptions_current_snapshot
        FOREIGN KEY (current_snapshot_id) REFERENCES subscription_snapshots (id);

CREATE TABLE subscription_snapshot_items (
    snapshot_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT pk_subscription_snapshot_items PRIMARY KEY (snapshot_id, sku_id),
    CONSTRAINT fk_snapshot_items_snapshot FOREIGN KEY (snapshot_id) REFERENCES subscription_snapshots (id),
    CONSTRAINT fk_snapshot_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_snapshot_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_snapshot_id BIGINT NULL,
    CONSTRAINT pk_subscription_schedules PRIMARY KEY (id),
    CONSTRAINT fk_schedules_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_schedules_snapshot FOREIGN KEY (effective_snapshot_id) REFERENCES subscription_snapshots (id),
    CONSTRAINT uk_schedules_subscription_date UNIQUE (subscription_id, scheduled_date),
    CONSTRAINT chk_schedules_status CHECK (status IN ('SCHEDULED', 'SKIPPED', 'HELD', 'CANCELED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_schedules_subscription_date ON subscription_schedules (subscription_id, scheduled_date, id);

CREATE TABLE pending_plan_changes (
    subscription_id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    target_schedule_id BIGINT NOT NULL,
    CONSTRAINT pk_pending_plan_changes PRIMARY KEY (subscription_id),
    CONSTRAINT uk_pending_snapshot UNIQUE (snapshot_id),
    CONSTRAINT uk_pending_target_schedule UNIQUE (target_schedule_id),
    CONSTRAINT fk_pending_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_pending_snapshot FOREIGN KEY (snapshot_id) REFERENCES subscription_snapshots (id),
    CONSTRAINT fk_pending_target FOREIGN KEY (target_schedule_id) REFERENCES subscription_schedules (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_command_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    version_before BIGINT NOT NULL,
    version_after BIGINT NOT NULL,
    CONSTRAINT pk_subscription_command_history PRIMARY KEY (id),
    CONSTRAINT fk_command_history_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_command_history_subscription_occurred ON subscription_command_history (subscription_id, occurred_at, id);

CREATE TABLE subscription_creation_idempotency_results (
    member_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    subscription_id BIGINT NULL,
    response_status INT NULL,
    response_body JSON NULL,
    location_header VARCHAR(512) NULL,
    etag_header VARCHAR(64) NULL,
    CONSTRAINT pk_creation_idempotency PRIMARY KEY (member_id, idempotency_key),
    CONSTRAINT fk_creation_idempotency_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_creation_idempotency_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_command_idempotency_results (
    member_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint CHAR(64) NOT NULL,
    response_status INT NULL,
    response_body JSON NULL,
    location_header VARCHAR(512) NULL,
    etag_header VARCHAR(64) NULL,
    CONSTRAINT pk_command_idempotency PRIMARY KEY (member_id, subscription_id, command_type, idempotency_key),
    CONSTRAINT fk_command_idempotency_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_command_idempotency_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;