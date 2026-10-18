package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
public record SubscriptionItemDetailResponse(
    long skuId,
    String skuName,
    long productId,
    String productName,
    String thumbnailUrl,
    int quantity) {
  public static SubscriptionItemDetailResponse from(SubscriptionReadModel.ItemDetail view) {
    return new SubscriptionItemDetailResponse(
        view.skuId(),
        view.skuName(),
        view.productId(),
        view.productName(),
        view.thumbnailUrl(),
        view.quantity());
  }
}
