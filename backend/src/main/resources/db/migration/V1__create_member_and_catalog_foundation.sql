CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    description VARCHAR(2000) NULL,
    pet_type VARCHAR(20) NOT NULL,
    thumbnail_url VARCHAR(2048) NULL,
    display_status VARCHAR(20) NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE skus (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    subscribable BOOLEAN NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_skus PRIMARY KEY (id),
    CONSTRAINT fk_skus_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_skus_price_nonnegative CHECK (price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_skus_product_display
    ON skus (product_id, display_order, id);
