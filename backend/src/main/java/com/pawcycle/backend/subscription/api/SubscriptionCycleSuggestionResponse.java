package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.RepeatCommerceReadModel;
import java.util.List;

public record SubscriptionCycleSuggestionResponse(
    long subscriptionId,
    int currentDeliveryCycleWeeks,
    long medianSuccessfulIntervalWeeks,
    List<Integer> allowedDeliveryCycleWeeks,
    Suggestion suggestion) {
  public record Suggestion(int deliveryCycleWeeks) {
    public static Suggestion from(RepeatCommerceReadModel.SubscriptionCycleSuggestion.Suggestion value) {
      return new Suggestion(value.deliveryCycleWeeks());
    }
  }
  public static SubscriptionCycleSuggestionResponse from(RepeatCommerceReadModel.SubscriptionCycleSuggestion value) {
    return new SubscriptionCycleSuggestionResponse(value.subscriptionId(), value.currentDeliveryCycleWeeks(),
        value.medianSuccessfulIntervalWeeks(), value.allowedDeliveryCycleWeeks(),
        value.suggestion() == null ? null : Suggestion.from(value.suggestion()));
  }
}
