ALTER TABLE refunds
    DROP CHECK chk_refunds_amount,
    ADD CONSTRAINT chk_refunds_amount CHECK (amount >= 0);
