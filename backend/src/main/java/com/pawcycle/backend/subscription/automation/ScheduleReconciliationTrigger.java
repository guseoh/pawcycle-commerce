package com.pawcycle.backend.subscription.automation;

import com.pawcycle.backend.common.scheduling.ScheduledJob;
import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import com.pawcycle.backend.subscription.application.SubscriptionReconciliationApplicationService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This trigger is separate from normal order automation. It never consumes an unprocessed due
 * Schedule and only repairs safely derivable cardinality after an Order already exists.
 */
@Component
@ConditionalOnProperty(
    prefix = "pawcycle.subscription.reconciliation",
    name = "enabled",
    havingValue = "true")
public class ScheduleReconciliationTrigger {

  private final SubscriptionReconciliationApplicationService service;
  private final ScheduledJobRunner jobs;

  public ScheduleReconciliationTrigger(
      SubscriptionReconciliationApplicationService service, ScheduledJobRunner jobs) {
    this.service = service;
    this.jobs = jobs;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.reconciliation.fixed-delay-ms:60000}")
  public void reconcileActiveSubscriptions() {
    jobs.run(ScheduledJob.SUBSCRIPTION_RECONCILIATION, service::reconcileActiveSubscriptions);
  }
}
