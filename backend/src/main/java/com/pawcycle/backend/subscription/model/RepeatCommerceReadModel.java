package com.pawcycle.backend.subscription.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RepeatCommerceReadModel {
  private RepeatCommerceReadModel() {}

  public record RepeatPurchase(List<Item> items) {
    public record Item(
        long productId,
        String productName,
        LocalDate lastPurchasedDate,
        LocalDate expectedReorderDate,
        String state,
        int purchaseCount) {}
  }

  public record SubscriptionOptions(long orderId, List<Option> options) {
    public record Option(
        long planVersionId,
        String planName,
        List<Long> matchingProductIds,
        List<Long> compatibleOwnedPetIds,
        List<Integer> allowedDeliveryCycleWeeks,
        BigDecimal packagePriceKrw) {}
  }

  public record SubscriptionCycleSuggestion(
      long subscriptionId,
      int currentDeliveryCycleWeeks,
      long medianSuccessfulIntervalWeeks,
      List<Integer> allowedDeliveryCycleWeeks,
      Suggestion suggestion) {
    public record Suggestion(int deliveryCycleWeeks) {}
  }
}
