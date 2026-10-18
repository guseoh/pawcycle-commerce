package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.math.BigDecimal;

public record SubscriptionAddonResponse(
    long skuId,
    long productId,
    String productName,
    String skuName,
    int quantity,
    BigDecimal unitPriceKrw,
    BigDecimal lineAmountKrw) {
  public static SubscriptionAddonResponse from(SubscriptionReadModel.Addon view) {
    return new SubscriptionAddonResponse(
        view.skuId(),
        view.productId(),
        view.productName(),
        view.skuName(),
        view.quantity(),
        view.unitPriceKrw(),
        view.lineAmountKrw());
  }
}
