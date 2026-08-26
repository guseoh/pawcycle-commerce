ALTER TABLE checkout_idempotency_results
    ADD COLUMN request_cart_version BIGINT NULL,
    ADD CONSTRAINT chk_checkout_idempotency_request_cart_version
        CHECK (request_cart_version IS NULL OR request_cart_version >= 0);
