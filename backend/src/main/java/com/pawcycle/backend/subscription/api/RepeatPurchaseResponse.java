package com.pawcycle.backend.subscription.api;

import java.time.LocalDate;
import java.util.List;

public record RepeatPurchaseResponse(List<Item> items) {
  public record Item(
      long productId,
      String productName,
      LocalDate lastPurchasedDate,
      LocalDate expectedReorderDate,
      String state,
      int purchaseCount) {}
}
