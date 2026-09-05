package com.pawcycle.backend.subscription;

import org.springframework.stereotype.Service;

/** Batch orchestration facade; SQL and candidate processing live in the feature processor. */
@Service
public class SubscriptionOrderAutomationService {
  private final SubscriptionOrderProcessor processor;

  public SubscriptionOrderAutomationService(SubscriptionOrderProcessor processor) {
    this.processor = processor;
  }

  public SubscriptionAutomationBatchResult processDueSchedules(int batchSize) {
    return processor.processDueSchedules(batchSize);
  }

  public static java.time.LocalDate firstFutureDate(
      java.time.LocalDate from, int cycleWeeks, java.time.LocalDate today) {
    return SubscriptionOrderProcessor.firstFutureDate(from, cycleWeeks, today);
  }
}
