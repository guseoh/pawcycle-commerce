ALTER TABLE subscription_creation_idempotency_results
    ADD COLUMN completed_at DATETIME(6) NULL;
