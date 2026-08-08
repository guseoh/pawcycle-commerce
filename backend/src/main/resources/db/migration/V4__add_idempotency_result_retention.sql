ALTER TABLE subscription_creation_idempotency_results
    ADD COLUMN completed_at DATETIME(6) NULL;

ALTER TABLE subscription_command_idempotency_results
    ADD COLUMN completed_at DATETIME(6) NULL;

SET @v4_completed_at = UTC_TIMESTAMP(6);

UPDATE subscription_creation_idempotency_results
SET completed_at = @v4_completed_at
WHERE response_status BETWEEN 200 AND 299
  AND response_body IS NOT NULL;

UPDATE subscription_command_idempotency_results
SET completed_at = @v4_completed_at
WHERE response_status BETWEEN 200 AND 299
  AND response_body IS NOT NULL;

CREATE INDEX idx_creation_idempotency_completed_at
    ON subscription_creation_idempotency_results (completed_at, member_id, idempotency_key);

CREATE INDEX idx_command_idempotency_completed_at
    ON subscription_command_idempotency_results (completed_at, member_id, subscription_id, command_type, idempotency_key);
