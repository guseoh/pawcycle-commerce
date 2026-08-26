CREATE TABLE product_detail_sections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    display_order INT NOT NULL,
    visible BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_detail_sections PRIMARY KEY (id),
    CONSTRAINT fk_product_detail_sections_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_detail_sections_display_order CHECK (display_order >= 0),
    INDEX ix_product_detail_sections_product_order (product_id, visible, display_order, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    content TEXT NOT NULL,
    visible BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uk_reviews_member_product UNIQUE (member_id, product_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX ix_reviews_product_visible_created (product_id, visible, created_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    answer TEXT NULL,
    answered_at DATETIME(6) NULL,
    visible BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_questions PRIMARY KEY (id),
    CONSTRAINT fk_product_questions_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_questions_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_product_questions_answer_state CHECK ((answer IS NULL AND answered_at IS NULL) OR (answer IS NOT NULL AND answered_at IS NOT NULL)),
    INDEX ix_product_questions_product_visible_created (product_id, visible, created_at, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_PAID','ORDER_SHIPPED','ORDER_DELIVERED','PAYMENT_ACTION_REQUIRED','CANCELLATION_COMPLETED','RETURN_APPROVED','RETURN_REJECTED','RETURN_COMPLETED','REFUND_ACTION_REQUIRED','SUBSCRIPTION_HELD','PRODUCT_QUESTION_ANSWERED'));
