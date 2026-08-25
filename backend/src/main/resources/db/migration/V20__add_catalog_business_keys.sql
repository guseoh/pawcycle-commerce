ALTER TABLE products
    ADD COLUMN catalog_key VARCHAR(150) NULL;

UPDATE products
SET catalog_key = CONCAT('legacy-product-', id)
WHERE catalog_key IS NULL;

CREATE UNIQUE INDEX uk_products_catalog_key ON products (catalog_key);

ALTER TABLE subscription_plans
    ADD COLUMN plan_key VARCHAR(150) NULL;

UPDATE subscription_plans
SET plan_key = CONCAT('legacy-plan-', id)
WHERE plan_key IS NULL;

CREATE UNIQUE INDEX uk_subscription_plans_plan_key ON subscription_plans (plan_key);
