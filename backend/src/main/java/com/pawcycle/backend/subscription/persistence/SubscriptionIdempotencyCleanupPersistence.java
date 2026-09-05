package com.pawcycle.backend.subscription.persistence;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionIdempotencyCleanupPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionIdempotencyCleanupPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int deleteExpiredCommands(Timestamp cutoff, int batchSize) {
    return jdbc.update(
        "DELETE FROM subscription_command_idempotency_results WHERE completed_at < ? ORDER BY"
            + " completed_at,member_id,subscription_id,command_type,idempotency_key LIMIT ?",
        cutoff,
        batchSize);
  }

  public int deleteExpiredCreations(Timestamp cutoff, int batchSize) {
    return jdbc.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE completed_at < ? ORDER"
            + " BY completed_at,member_id,idempotency_key LIMIT ?",
        cutoff,
        batchSize);
  }

  public int repairCommandCompletion(Timestamp now, int batchSize) {
    return jdbc.update(
        "UPDATE subscription_command_idempotency_results SET completed_at=? WHERE"
            + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
            + " response_body IS NOT NULL ORDER BY"
            + " member_id,subscription_id,command_type,idempotency_key LIMIT ?",
        now,
        batchSize);
  }

  public int repairCreationCompletion(Timestamp now, int batchSize) {
    return jdbc.update(
        "UPDATE subscription_creation_idempotency_results SET completed_at=? WHERE"
            + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
            + " response_body IS NOT NULL ORDER BY member_id,idempotency_key LIMIT ?",
        now,
        batchSize);
  }
}
