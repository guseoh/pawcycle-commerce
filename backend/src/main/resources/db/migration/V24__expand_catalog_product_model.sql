CREATE TABLE brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    logo_url VARCHAR(2048) NULL,
    active BOOLEAN NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_brands PRIMARY KEY (id),
    CONSTRAINT uk_brands_slug UNIQUE (slug),
    CONSTRAINT chk_brands_display_order_nonnegative CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A stable local/demo default keeps all existing products and manifests assignable.
INSERT INTO brands (id, name, slug, logo_url, active, display_order)
VALUES (1, 'PawCycle Demo Catalog', 'pawcycle-demo-catalog', NULL, true, 0);

ALTER TABLE products
    ADD COLUMN brand_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id);

UPDATE products SET brand_id = 1 WHERE brand_id IS NULL;

ALTER TABLE products
    MODIFY COLUMN brand_id BIGINT NOT NULL;

CREATE INDEX idx_products_brand ON products (brand_id, id);

ALTER TABLE categories
    ADD COLUMN parent_id BIGINT NULL AFTER id,
    ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id);

CREATE INDEX idx_categories_parent_order ON categories (parent_id, display_order, id);

CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    alt_text VARCHAR(500) NULL,
    display_order INT NOT NULL,
    image_type VARCHAR(20) NOT NULL,
    main_product_id BIGINT GENERATED ALWAYS AS (CASE WHEN image_type = 'MAIN' THEN product_id ELSE NULL END) STORED,
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_product_images_main UNIQUE (main_product_id),
    CONSTRAINT chk_product_images_type CHECK (image_type IN ('MAIN', 'DETAIL')),
    CONSTRAINT chk_product_images_display_order_nonnegative CHECK (display_order >= 0),
    INDEX idx_product_images_product_order (product_id, display_order, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE skus
    ADD COLUMN compare_at_price DECIMAL(12,2) NULL AFTER price,
    ADD CONSTRAINT chk_skus_compare_at_price CHECK (compare_at_price IS NULL OR compare_at_price > price);

CREATE TABLE product_option_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_product_option_groups PRIMARY KEY (id),
    CONSTRAINT fk_product_option_groups_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_product_option_groups_name UNIQUE (product_id, name),
    CONSTRAINT chk_product_option_groups_display_order_nonnegative CHECK (display_order >= 0),
    INDEX idx_product_option_groups_product_order (product_id, display_order, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_option_values (
    id BIGINT NOT NULL AUTO_INCREMENT,
    option_group_id BIGINT NOT NULL,
    value VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_product_option_values PRIMARY KEY (id),
    CONSTRAINT fk_product_option_values_group FOREIGN KEY (option_group_id) REFERENCES product_option_groups (id),
    CONSTRAINT uk_product_option_values_group_value UNIQUE (option_group_id, value),
    CONSTRAINT chk_product_option_values_display_order_nonnegative CHECK (display_order >= 0),
    INDEX idx_product_option_values_group_order (option_group_id, display_order, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sku_option_values (
    sku_id BIGINT NOT NULL,
    option_value_id BIGINT NOT NULL,
    CONSTRAINT pk_sku_option_values PRIMARY KEY (sku_id, option_value_id),
    CONSTRAINT fk_sku_option_values_sku FOREIGN KEY (sku_id) REFERENCES skus (id),
    CONSTRAINT fk_sku_option_values_value FOREIGN KEY (option_value_id) REFERENCES product_option_values (id),
    INDEX idx_sku_option_values_value (option_value_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE facet_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    `key` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_facet_definitions PRIMARY KEY (id),
    CONSTRAINT uk_facet_definitions_key UNIQUE (`key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE facet_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facet_definition_id BIGINT NOT NULL,
    value VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_facet_options PRIMARY KEY (id),
    CONSTRAINT fk_facet_options_definition FOREIGN KEY (facet_definition_id) REFERENCES facet_definitions (id),
    CONSTRAINT uk_facet_options_definition_value UNIQUE (facet_definition_id, value),
    CONSTRAINT chk_facet_options_display_order_nonnegative CHECK (display_order >= 0),
    INDEX idx_facet_options_definition_order (facet_definition_id, display_order, id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE category_facets (
    category_id BIGINT NOT NULL,
    facet_definition_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT pk_category_facets PRIMARY KEY (category_id, facet_definition_id),
    CONSTRAINT fk_category_facets_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_category_facets_definition FOREIGN KEY (facet_definition_id) REFERENCES facet_definitions (id),
    CONSTRAINT chk_category_facets_display_order_nonnegative CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_facet_values (
    product_id BIGINT NOT NULL,
    facet_option_id BIGINT NOT NULL,
    CONSTRAINT pk_product_facet_values PRIMARY KEY (product_id, facet_option_id),
    CONSTRAINT fk_product_facet_values_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_facet_values_option FOREIGN KEY (facet_option_id) REFERENCES facet_options (id),
    INDEX idx_product_facet_values_option (facet_option_id, product_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
