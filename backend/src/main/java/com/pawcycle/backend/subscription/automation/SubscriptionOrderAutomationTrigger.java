package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "pawcycle.subscription.automation",
    name = "enabled",
    havingValue = "true")
public class SubscriptionOrderAutomationTrigger {

  private final SubscriptionOrderAutomationService service;
  private final int batchSize;
  private final ScheduledJobRunner jobs;

  public SubscriptionOrderAutomationTrigger(
      SubscriptionOrderAutomationService service,
      @Value("${pawcycle.subscription.automation.batch-size:100}") int batchSize,
      ScheduledJobRunner jobs) {
    if (batchSize < 1) {
      throw new IllegalArgumentException(
          "pawcycle.subscription.automation.batch-size must be positive");
    }
    this.service = service;
    this.batchSize = batchSize;
    this.jobs = jobs;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.automation.fixed-delay-ms:60000}")
  public void processDueSchedules() {
    jobs.run(
        ScheduledJob.SUBSCRIPTION_ORDER_AUTOMATION,
        () -> service.processDueSchedules(batchSize).processedCandidates());
  }
}
