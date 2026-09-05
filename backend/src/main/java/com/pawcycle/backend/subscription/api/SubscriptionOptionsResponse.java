package com.pawcycle.backend.subscription.api;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionOptionsResponse(long orderId, List<Option> options) {
  public record Option(
      long planVersionId,
      String planName,
      List<Long> matchingProductIds,
      List<Long> compatibleOwnedPetIds,
      List<Integer> allowedDeliveryCycleWeeks,
      BigDecimal packagePriceKrw) {}
}
