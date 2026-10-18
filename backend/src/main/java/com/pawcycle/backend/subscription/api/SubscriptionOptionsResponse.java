package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.RepeatCommerceReadModel;
import java.math.BigDecimal;
import java.util.List;

public record SubscriptionOptionsResponse(long orderId, List<Option> options) {
  public record Option(
      long planVersionId,
      String planName,
      List<Long> matchingProductIds,
      List<Long> compatibleOwnedPetIds,
      List<Integer> allowedDeliveryCycleWeeks,
      BigDecimal packagePriceKrw) {
    public static Option from(RepeatCommerceReadModel.SubscriptionOptions.Option value) {
      return new Option(value.planVersionId(), value.planName(), value.matchingProductIds(),
          value.compatibleOwnedPetIds(), value.allowedDeliveryCycleWeeks(), value.packagePriceKrw());
    }
  }
  public static SubscriptionOptionsResponse from(RepeatCommerceReadModel.SubscriptionOptions value) {
    return new SubscriptionOptionsResponse(value.orderId(), value.options() == null ? null : value.options().stream()
        .map(option -> option == null ? null : Option.from(option))
        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
  }
}
