package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionIdempotencyCleanupPersistence {
  private final SubscriptionNativeSql nativeSql;

  public SubscriptionIdempotencyCleanupPersistence(EntityManager entities) {
    this.nativeSql = new SubscriptionNativeSql(entities);
  }

  public int deleteExpiredCommands(LocalDateTime cutoff, int batchSize) {
    return nativeSql.update(
        "DELETE FROM subscription_command_idempotency_results WHERE completed_at < ? ORDER BY"
            + " completed_at,member_id,subscription_id,command_type,idempotency_key LIMIT ?",
        cutoff,
        batchSize);
  }

  public int deleteExpiredCreations(LocalDateTime cutoff, int batchSize) {
    return nativeSql.update(
        "DELETE FROM subscription_creation_idempotency_results WHERE completed_at < ? ORDER"
            + " BY completed_at,member_id,idempotency_key LIMIT ?",
        cutoff,
        batchSize);
  }

  public int repairCommandCompletion(LocalDateTime now, int batchSize) {
    return nativeSql.update(
        "UPDATE subscription_command_idempotency_results SET completed_at=? WHERE"
            + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
            + " response_body IS NOT NULL ORDER BY"
            + " member_id,subscription_id,command_type,idempotency_key LIMIT ?",
        now,
        batchSize);
  }

  public int repairCreationCompletion(LocalDateTime now, int batchSize) {
    return nativeSql.update(
        "UPDATE subscription_creation_idempotency_results SET completed_at=? WHERE"
            + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
            + " response_body IS NOT NULL ORDER BY member_id,idempotency_key LIMIT ?",
        now,
        batchSize);
  }
}
