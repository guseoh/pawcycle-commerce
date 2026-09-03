package com.pawcycle.backend.subscription;

import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SubscriptionIdempotencyCleanupService {
  private static final Duration RETENTION = Duration.ofDays(30);
  private final NativeQueryExecutor jdbc;
  private final Clock clock;
  private final SubscriptionMetrics metrics;

  public SubscriptionIdempotencyCleanupService(
      NativeQueryExecutor jdbc, Clock clock, SubscriptionMetrics metrics) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.metrics = metrics;
  }

  @Transactional
  public SubscriptionIdempotencyCleanupResult deleteExpired(int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }

    Timer.Sample sample = metrics.startCleanup();
    AtomicReference<SubscriptionIdempotencyCleanupResult> completedResult = new AtomicReference<>();
    boolean transactionSynchronized = TransactionSynchronizationManager.isSynchronizationActive();
    if (transactionSynchronized) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              SubscriptionIdempotencyCleanupResult result = completedResult.get();
              if (status == STATUS_COMMITTED && result != null) {
                metrics.cleanupSucceeded(result);
              } else {
                metrics.cleanupFailed();
              }
              metrics.finishCleanup(sample);
            }
          });
    }
    try {
      LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
      Timestamp nowTimestamp = Timestamp.from(now.toInstant(ZoneOffset.UTC));
      int creationRepaired =
          jdbc.update(
              "UPDATE subscription_creation_idempotency_results SET completed_at=? WHERE"
                  + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
                  + " response_body IS NOT NULL ORDER BY member_id,idempotency_key LIMIT ?",
              nowTimestamp,
              batchSize);
      int commandRepaired =
          jdbc.update(
              "UPDATE subscription_command_idempotency_results SET completed_at=? WHERE"
                  + " completed_at IS NULL AND response_status BETWEEN 200 AND 299 AND"
                  + " response_body IS NOT NULL ORDER BY"
                  + " member_id,subscription_id,command_type,idempotency_key LIMIT ?",
              nowTimestamp,
              batchSize);
      LocalDateTime cutoff = now.minus(RETENTION);
      Timestamp cutoffTimestamp = Timestamp.from(cutoff.toInstant(ZoneOffset.UTC));
      int creationDeleted =
          jdbc.update(
              "DELETE FROM subscription_creation_idempotency_results WHERE completed_at < ? ORDER"
                  + " BY completed_at,member_id,idempotency_key LIMIT ?",
              cutoffTimestamp,
              batchSize);
      int commandDeleted =
          jdbc.update(
              "DELETE FROM subscription_command_idempotency_results WHERE completed_at < ? ORDER BY"
                  + " completed_at,member_id,subscription_id,command_type,idempotency_key LIMIT ?",
              cutoffTimestamp,
              batchSize);
      SubscriptionIdempotencyCleanupResult result =
          new SubscriptionIdempotencyCleanupResult(
              creationRepaired, commandRepaired, creationDeleted, commandDeleted);
      completedResult.set(result);
      if (!transactionSynchronized) {
        metrics.cleanupSucceeded(result);
      }
      return result;
    } catch (RuntimeException exception) {
      if (!transactionSynchronized) {
        metrics.cleanupFailed();
      }
      throw exception;
    } finally {
      if (!transactionSynchronized) {
        metrics.finishCleanup(sample);
      }
    }
  }

}
