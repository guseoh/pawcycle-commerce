package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.subscription.persistence.SubscriptionIdempotencyCleanupPersistence;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SubscriptionIdempotencyCleanupProcessor {
  private static final Duration RETENTION = Duration.ofDays(30);
  private final SubscriptionIdempotencyCleanupPersistence persistence;
  private final Clock clock;
  private final SubscriptionMetrics metrics;

  public SubscriptionIdempotencyCleanupProcessor(
      SubscriptionIdempotencyCleanupPersistence persistence,
      Clock clock,
      SubscriptionMetrics metrics) {
    this.persistence = persistence;
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
      int creationRepaired = persistence.repairCreationCompletion(now, batchSize);
      int commandRepaired = persistence.repairCommandCompletion(now, batchSize);
      LocalDateTime cutoff = now.minus(RETENTION);
      int creationDeleted = persistence.deleteExpiredCreations(cutoff, batchSize);
      int commandDeleted = persistence.deleteExpiredCommands(cutoff, batchSize);
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
