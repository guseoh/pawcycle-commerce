INSERT INTO categories (name, slug, display_order, active)
VALUES ('미분류', '__pawcycle_uncategorized__', 2147483647, false);

UPDATE products
SET category_id = (SELECT id FROM categories WHERE slug = '__pawcycle_uncategorized__')
WHERE category_id IS NULL;

ALTER TABLE products
    MODIFY COLUMN category_id BIGINT NOT NULL;

CREATE TABLE inventories (
    sku_id BIGINT NOT NULL,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventories PRIMARY KEY (sku_id),
    CONSTRAINT fk_inventories_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_inventories_available_nonnegative CHECK (available_quantity >= 0),
    CONSTRAINT chk_inventories_reserved_nonnegative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_inventories_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO inventories (sku_id, available_quantity, reserved_quantity, version)
SELECT id, 0, 0, 0 FROM skus;

CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_member UNIQUE (member_id),
    CONSTRAINT fk_carts_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cart_items (
    cart_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (cart_id, sku_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wishlist_items (
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_wishlist_items PRIMARY KEY (member_id, product_id),
    CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_member_addresses PRIMARY KEY (id),
    CONSTRAINT fk_member_addresses_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE members
    ADD COLUMN default_address_id BIGINT NULL,
    ADD CONSTRAINT fk_members_default_address FOREIGN KEY (default_address_id) REFERENCES member_addresses (id);

CREATE TABLE coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    minimum_order_amount DECIMAL(12,2) NOT NULL,
    maximum_discount_amount DECIMAL(12,2) NULL,
    valid_from DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT chk_coupons_type CHECK (discount_type IN ('FIXED_AMOUNT','PERCENTAGE')),
    CONSTRAINT chk_coupons_amounts CHECK (discount_value >= 0 AND minimum_order_amount >= 0 AND (maximum_discount_amount IS NULL OR maximum_discount_amount >= 0)),
    CONSTRAINT chk_coupons_valid_range CHECK (valid_until > valid_from)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reserved_order_id BIGINT NULL,
    issued_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    CONSTRAINT pk_member_coupons PRIMARY KEY (id),
    CONSTRAINT fk_member_coupons_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT chk_member_coupons_status CHECK (status IN ('AVAILABLE','RESERVED','USED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE membership_grades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    minimum_purchase_amount DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL,
    active BOOLEAN NOT NULL,
    benefit_coupon_id BIGINT NULL,
    CONSTRAINT pk_membership_grades PRIMARY KEY (id),
    CONSTRAINT uk_membership_grades_code UNIQUE (code),
    CONSTRAINT fk_membership_grades_coupon FOREIGN KEY (benefit_coupon_id) REFERENCES coupons (id),
    CONSTRAINT chk_membership_grades_minimum CHECK (minimum_purchase_amount >= 0),
    CONSTRAINT chk_membership_grades_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO membership_grades (code, name, minimum_purchase_amount, display_order, active)
VALUES ('BASIC', 'BASIC', 0, 0, true);

CREATE TABLE member_memberships (
    member_id BIGINT NOT NULL,
    grade_id BIGINT NOT NULL,
    evaluated_purchase_amount DECIMAL(12,2) NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_member_memberships PRIMARY KEY (member_id),
    CONSTRAINT fk_member_memberships_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_memberships_grade FOREIGN KEY (grade_id) REFERENCES membership_grades (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE membership_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    from_grade_id BIGINT NULL,
    to_grade_id BIGINT NOT NULL,
    evaluated_purchase_amount DECIMAL(12,2) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_membership_histories PRIMARY KEY (id),
    CONSTRAINT fk_membership_histories_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_membership_histories_from_grade FOREIGN KEY (from_grade_id) REFERENCES membership_grades (id),
    CONSTRAINT fk_membership_histories_to_grade FOREIGN KEY (to_grade_id) REFERENCES membership_grades (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
