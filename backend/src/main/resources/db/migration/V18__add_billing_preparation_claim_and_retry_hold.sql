ALTER TABLE billing_payment_method_preparations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'READY' AFTER customer_key,
    ADD COLUMN claimed_at DATETIME(6) NULL AFTER expires_at,
    ADD CONSTRAINT chk_billing_preparations_status CHECK (status IN ('READY','PROCESSING'));

ALTER TABLE subscription_schedules
    DROP CHECK chk_subscription_schedules_hold_reason,
    ADD CONSTRAINT chk_subscription_schedules_hold_reason CHECK (
        hold_reason IS NULL OR hold_reason IN (
            'MISSING_SHIPPING_ADDRESS',
            'MISSING_BILLING_METHOD',
            'PAYMENT_RETRY_EXHAUSTED',
            'PAYMENT_RETRY_STOCK_UNAVAILABLE'
        )
    );
