CREATE INDEX idx_command_idempotency_completed_at
    ON subscription_command_idempotency_results (completed_at, member_id, subscription_id, command_type, idempotency_key);
