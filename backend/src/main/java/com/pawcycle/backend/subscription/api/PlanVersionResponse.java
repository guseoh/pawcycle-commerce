package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.util.List;

public record PlanVersionResponse(
    long planId,
    String planName,
    String targetPetType,
    long planVersionId,
    long packagePriceKrw,
    List<PlanItemResponse> items,
    List<Integer> allowedDeliveryCycleWeeks,
    PlanSaleResponse sale) {
  public static PlanVersionResponse from(SubscriptionReadModel.PlanVersion value) {
    return new PlanVersionResponse(value.planId(), value.planName(), value.targetPetType(),
        value.planVersionId(), value.packagePriceKrw(), value.items() == null ? null :
        value.items().stream().map(item -> item == null ? null : PlanItemResponse.from(item)).toList(),
        value.allowedDeliveryCycleWeeks(), value.sale() == null ? null : PlanSaleResponse.from(value.sale()));
  }
}
