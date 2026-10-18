package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.order.model.OrderReadModel;
import java.util.List;

public record OrderReorderResponse(
    List<Item> addedItems, List<SkippedItem> skippedItems, long cartVersion) {

  public record Item(long skuId, int quantity) {}

  public record SkippedItem(long skuId, int quantity, String reason) {}
  public static OrderReorderResponse from(OrderReadModel.Reorder view) {
    return new OrderReorderResponse(
        view.addedItems().stream().map(item -> new Item(item.skuId(), item.quantity())).toList(),
        view.skippedItems().stream().map(item -> new SkippedItem(item.skuId(), item.quantity(), item.reason())).toList(),
        view.cartVersion());
  }
}
