package com.pawcycle.backend.commerce.order.api;

import java.util.List;

public record OrderReorderResponse(
    List<Item> addedItems, List<SkippedItem> skippedItems, long cartVersion) {

  public record Item(long skuId, int quantity) {}

  public record SkippedItem(long skuId, int quantity, String reason) {}
}
