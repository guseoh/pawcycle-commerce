ALTER TABLE products
    ADD CONSTRAINT chk_products_display_status
        CHECK (BINARY display_status IN ('DRAFT', 'PUBLIC', 'INACTIVE'));

ALTER TABLE members
    ADD COLUMN role VARCHAR(20) NULL AFTER password_hash;

UPDATE members
SET role = 'USER'
WHERE role IS NULL;

ALTER TABLE members
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_members_role
        CHECK (BINARY role IN ('USER', 'ADMIN'));

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_order INT NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT chk_categories_display_order_nonnegative CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE products
    ADD COLUMN category_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories (id);

CREATE INDEX idx_products_category
    ON products (category_id, id);

ALTER TABLE skus
    ADD COLUMN sku_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER product_id,
    ADD COLUMN status VARCHAR(20) NULL AFTER display_order;

UPDATE skus
SET sku_code = CONCAT('SKU-', id),
    status = 'ACTIVE'
WHERE sku_code IS NULL
   OR status IS NULL;

ALTER TABLE skus
    MODIFY COLUMN sku_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL,
    ADD CONSTRAINT uk_skus_sku_code UNIQUE (sku_code),
    ADD CONSTRAINT chk_skus_status
        CHECK (BINARY status IN ('ACTIVE', 'INACTIVE'));
