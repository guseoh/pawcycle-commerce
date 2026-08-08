SET @v6_completed_at = UTC_TIMESTAMP(6);

UPDATE subscription_creation_idempotency_results
SET completed_at = @v6_completed_at
WHERE response_status BETWEEN 200 AND 299
  AND response_body IS NOT NULL
  AND completed_at IS NULL;

UPDATE subscription_command_idempotency_results
SET completed_at = @v6_completed_at
WHERE response_status BETWEEN 200 AND 299
  AND response_body IS NOT NULL
  AND completed_at IS NULL;
