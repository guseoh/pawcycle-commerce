ALTER TABLE pets
    ADD COLUMN breed VARCHAR(80) NULL AFTER pet_type,
    ADD COLUMN weight_kg DECIMAL(5,2) NULL AFTER breed,
    ADD CONSTRAINT chk_pets_weight_kg CHECK (weight_kg IS NULL OR (weight_kg > 0 AND weight_kg <= 200.00));

CREATE TABLE interaction_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    product_id BIGINT NULL,
    pet_id BIGINT NULL,
    source VARCHAR(100) NULL,
    recommendation_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    context JSON NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_interaction_events PRIMARY KEY (id),
    CONSTRAINT uk_interaction_events_member_event UNIQUE (member_id, event_id),
    CONSTRAINT fk_interaction_events_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_interaction_events_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_interaction_events_pet FOREIGN KEY (pet_id) REFERENCES pets (id),
    CONSTRAINT chk_interaction_events_type CHECK (event_type IN ('PRODUCT_IMPRESSION','PRODUCT_VIEW','SEARCH','FILTER','RECOMMENDATION_IMPRESSION','RECOMMENDATION_CLICK')),
    CONSTRAINT chk_interaction_events_recommendation_fields CHECK (
        event_type NOT IN ('RECOMMENDATION_IMPRESSION','RECOMMENDATION_CLICK')
        OR (product_id IS NOT NULL AND recommendation_request_id IS NOT NULL)
    ),
    INDEX ix_interaction_events_member_occurred (member_id, occurred_at, id),
    INDEX ix_interaction_events_member_product_occurred (member_id, product_id, occurred_at, id),
    INDEX ix_interaction_events_type_occurred_product (event_type, occurred_at, product_id),
    INDEX ix_interaction_events_pet_occurred (pet_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
