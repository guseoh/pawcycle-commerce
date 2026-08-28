CREATE TABLE product_review_summaries (
    product_id BIGINT NOT NULL,
    source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    summary VARCHAR(500) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_product_review_summaries PRIMARY KEY (product_id),
    CONSTRAINT fk_product_review_summaries_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_review_summaries_summary CHECK (CHAR_LENGTH(summary) BETWEEN 1 AND 500)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE notifications
    DROP CONSTRAINT chk_notifications_type,
    ADD CONSTRAINT chk_notifications_type CHECK (type IN ('ORDER_PAID','ORDER_SHIPPED','ORDER_DELIVERED','PAYMENT_ACTION_REQUIRED','CANCELLATION_COMPLETED','RETURN_APPROVED','RETURN_REJECTED','RETURN_COMPLETED','REFUND_ACTION_REQUIRED','SUBSCRIPTION_HELD','PRODUCT_QUESTION_ANSWERED','SUBSCRIPTION_DELIVERY_REMINDER'));
