package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionCommand;
import java.time.LocalDate;

public record SubscriptionCommandRequest(
    Long petId,
    Long planVersionId,
    Integer deliveryCycleWeeks,
    LocalDate scheduledDate,
    Long skuId,
    Integer quantity) {
  public SubscriptionCommand toCommand() {
    return new SubscriptionCommand(
        petId, planVersionId, deliveryCycleWeeks, scheduledDate, skuId, quantity);
  }

  public static SubscriptionCommandRequest empty() {
    return new SubscriptionCommandRequest(null, null, null, null, null, null);
  }
}
