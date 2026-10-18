package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
public record SubscriptionItemResponse(long skuId, int quantity) {
  public static SubscriptionItemResponse from(SubscriptionReadModel.Item view) {
    return new SubscriptionItemResponse(
        view.skuId(),
        view.quantity());
  }
}
