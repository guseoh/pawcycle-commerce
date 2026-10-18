package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.order.model.OrderReadModel;
import java.math.BigDecimal;
import java.sql.Timestamp;

public record OrderSummaryResponse(
    long orderId,
    String orderNumber,
    String source,
    String status,
    BigDecimal paymentAmount,
    Timestamp createdAt,
    Timestamp paidAt) {
  public static OrderSummaryResponse from(OrderReadModel.Summary view) {
    return new OrderSummaryResponse(view.orderId(), view.orderNumber(), view.source(), view.status(), view.paymentAmount(), view.createdAt(), view.paidAt());
  }
}
