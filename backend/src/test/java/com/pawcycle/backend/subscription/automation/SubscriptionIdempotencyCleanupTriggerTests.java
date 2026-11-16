package com.pawcycle.backend.subscription.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SubscriptionIdempotencyCleanupTriggerTests {

  @Test
  void cleanupIsOffUnlessExplicitlyEnabled() {
    var runner = new ApplicationContextRunner()
        .withUserConfiguration(SubscriptionIdempotencyCleanupTrigger.class)
        .withBean(SubscriptionIdempotencyCleanupService.class, () -> mock(SubscriptionIdempotencyCleanupService.class))
        .withBean(ScheduledJobRunner.class, () -> mock(ScheduledJobRunner.class));

    runner.run(context -> assertThat(context).doesNotHaveBean(SubscriptionIdempotencyCleanupTrigger.class));
    runner.withPropertyValues("pawcycle.subscription.idempotency.cleanup.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(SubscriptionIdempotencyCleanupTrigger.class));
  }

  @Test
  void runRecordsEveryRepairedAndDeletedRowAsProcessedItems() {
    SubscriptionIdempotencyCleanupService cleanup = mock(SubscriptionIdempotencyCleanupService.class);
    when(cleanup.deleteExpired(50)).thenReturn(new SubscriptionIdempotencyCleanupResult(1, 2, 3, 4));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    new SubscriptionIdempotencyCleanupTrigger(cleanup, new ScheduledJobRunner(registry, Clock.systemUTC()), 50)
        .deleteExpired();

    verify(cleanup).deleteExpired(50);
    assertThat(registry.get("pawcycle.job.items").tag("job", "idempotency-cleanup").counter().count())
        .isEqualTo(10);
  }
}
