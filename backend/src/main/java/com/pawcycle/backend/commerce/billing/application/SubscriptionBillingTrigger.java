package com.pawcycle.backend.commerce.billing.application;

import com.pawcycle.backend.common.scheduling.SchedulerNames;
import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disabled by default; repository preparation never activates production billing scheduling. */
@Component
@ConditionalOnProperty(prefix = "pawcycle.commerce.billing", name = "enabled", havingValue = "true")
class SubscriptionBillingTrigger {
  private final SubscriptionBillingProcessor processor;
  private final ScheduledJobRunner jobs;

  SubscriptionBillingTrigger(SubscriptionBillingProcessor processor, ScheduledJobRunner jobs) {
    this.processor = processor;
    this.jobs = jobs;
  }

  // Own thread: each charge may wait up to 10 seconds on the provider (stage 2, O7).
  @Scheduled(
      scheduler = SchedulerNames.PROVIDER_CALLS,
      fixedDelayString = "${pawcycle.commerce.billing.fixed-delay-ms:60000}")
  void processReady() {
    jobs.run(ScheduledJob.SUBSCRIPTION_BILLING, processor::processReadyPayments);
  }
}
