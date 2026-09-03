package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;

public record SubscriptionCommandRequest(
    Long petId,
    Long planVersionId,
    Integer deliveryCycleWeeks,
    LocalDate scheduledDate,
    Long skuId,
    Integer quantity) {
  public static SubscriptionCommandRequest empty() {
    return new SubscriptionCommandRequest(null, null, null, null, null, null);
  }
}
