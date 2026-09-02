package com.pawcycle.backend.commerce;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disabled by default; repository preparation never activates production billing scheduling. */
@Component
@ConditionalOnProperty(prefix = "pawcycle.commerce.billing", name = "enabled", havingValue = "true")
class SubscriptionBillingTrigger {
  private final SubscriptionBillingProcessor processor;

  SubscriptionBillingTrigger(SubscriptionBillingProcessor processor) {
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${pawcycle.commerce.billing.fixed-delay-ms:60000}")
  void processReady() {
    processor.processReadyPayments();
  }
}
