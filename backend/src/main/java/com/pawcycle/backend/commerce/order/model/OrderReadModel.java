package com.pawcycle.backend.commerce.order.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/** Materialized order results; HTTP conversion and durable reorder JSON remain separate. */
public final class OrderReadModel {
  private OrderReadModel() {}

  public record Detail(OrderView view, List<String> availableActions) {}

  public record Summary(
      long orderId,
      String orderNumber,
      String source,
      String status,
      BigDecimal paymentAmount,
      Timestamp createdAt,
      Timestamp paidAt) {}

  public record AdminOrderView(
      long orderId,
      String orderNumber,
      long memberId,
      String status,
      BigDecimal paymentAmount,
      Timestamp createdAt) {}

  public record Reorder(List<ReorderItem> addedItems, List<SkippedItem> skippedItems, long cartVersion) {}

  public record ReorderItem(long skuId, int quantity) {}

  public record SkippedItem(long skuId, int quantity, String reason) {}
}
