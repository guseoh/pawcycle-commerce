package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.RepeatCommerceReadModel;
import java.time.LocalDate;
import java.util.List;

public record RepeatPurchaseResponse(List<Item> items) {
  public record Item(
      long productId,
      String productName,
      LocalDate lastPurchasedDate,
      LocalDate expectedReorderDate,
      String state,
      int purchaseCount) {
    public static Item from(RepeatCommerceReadModel.RepeatPurchase.Item value) {
      return new Item(value.productId(), value.productName(), value.lastPurchasedDate(),
          value.expectedReorderDate(), value.state(), value.purchaseCount());
    }
  }
  public static RepeatPurchaseResponse from(RepeatCommerceReadModel.RepeatPurchase value) {
    return new RepeatPurchaseResponse(value.items() == null ? null : value.items().stream()
        .map(item -> item == null ? null : Item.from(item)).toList());
  }
}
