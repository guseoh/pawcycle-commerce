package com.pawcycle.backend.subscription;

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

  public ScheduleReconciliationTrigger(SubscriptionReconciliationApplicationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${pawcycle.subscription.reconciliation.fixed-delay-ms:60000}")
  public void reconcileActiveSubscriptions() {
    service.reconcileActiveSubscriptions();
  }
}
