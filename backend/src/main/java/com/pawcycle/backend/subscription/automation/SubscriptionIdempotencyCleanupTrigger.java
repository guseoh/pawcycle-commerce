package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the existing 30-day idempotency retention cleanup on a schedule (known risk R9). Disabled by
 * default: it deletes rows, so enabling it in any shared environment is a separate decision.
 */
@Component
@ConditionalOnProperty(
    prefix = "pawcycle.subscription.idempotency.cleanup",
    name = "enabled",
    havingValue = "true")
public class SubscriptionIdempotencyCleanupTrigger {
  private final SubscriptionIdempotencyCleanupService cleanup;
  private final ScheduledJobRunner jobs;
  private final int batchSize;

  public SubscriptionIdempotencyCleanupTrigger(
      SubscriptionIdempotencyCleanupService cleanup,
      ScheduledJobRunner jobs,
      @Value("${pawcycle.subscription.idempotency.cleanup.batch-size:100}") int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException(
          "pawcycle.subscription.idempotency.cleanup.batch-size must be positive");
    }
    this.cleanup = cleanup;
    this.jobs = jobs;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.idempotency.cleanup.fixed-delay-ms:3600000}")
  public void deleteExpired() {
    jobs.run(ScheduledJob.IDEMPOTENCY_CLEANUP, () -> {
      SubscriptionIdempotencyCleanupResult result = cleanup.deleteExpired(batchSize);
      return result.creationRepaired() + result.commandRepaired()
          + result.creationDeleted() + result.commandDeleted();
    });
  }
}
