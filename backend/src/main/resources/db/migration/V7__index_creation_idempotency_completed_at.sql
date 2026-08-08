CREATE INDEX idx_creation_idempotency_completed_at
    ON subscription_creation_idempotency_results (completed_at, member_id, idempotency_key);
