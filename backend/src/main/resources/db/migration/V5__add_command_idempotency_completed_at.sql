ALTER TABLE subscription_command_idempotency_results
    ADD COLUMN completed_at DATETIME(6) NULL;
