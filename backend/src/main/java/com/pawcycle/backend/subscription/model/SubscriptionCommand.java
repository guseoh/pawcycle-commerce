package com.pawcycle.backend.subscription.model;

import java.time.LocalDate;

/** All command input participates in idempotency, including fields unused by a command type. */
public record SubscriptionCommand(
    Long petId, Long planVersionId, Integer deliveryCycleWeeks, LocalDate scheduledDate,
    Long skuId, Integer quantity) {
  public static SubscriptionCommand empty() {
    return new SubscriptionCommand(null, null, null, null, null, null);
  }
}
