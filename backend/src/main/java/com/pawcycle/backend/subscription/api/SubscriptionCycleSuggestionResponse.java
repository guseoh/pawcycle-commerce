package com.pawcycle.backend.subscription.api;

import java.util.List;

public record SubscriptionCycleSuggestionResponse(
    long subscriptionId,
    int currentDeliveryCycleWeeks,
    long medianSuccessfulIntervalWeeks,
    List<Integer> allowedDeliveryCycleWeeks,
    Suggestion suggestion) {
  public record Suggestion(int deliveryCycleWeeks) {}
}
