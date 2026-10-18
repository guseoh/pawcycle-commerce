package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;

public record PlanItemResponse(long skuId, int quantity) {
  public static PlanItemResponse from(SubscriptionReadModel.Item value) {
    return new PlanItemResponse(value.skuId(), value.quantity());
  }
}
