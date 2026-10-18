package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.order.model.OrderReadModel;
import java.math.BigDecimal;
import java.sql.Timestamp;

public record AdminOrderResponse(
    long orderId,
    String orderNumber,
    long memberId,
    String status,
    BigDecimal paymentAmount,
    Timestamp createdAt) {
  public static AdminOrderResponse from(OrderReadModel.AdminOrderView view) {
    return new AdminOrderResponse(view.orderId(), view.orderNumber(), view.memberId(), view.status(), view.paymentAmount(), view.createdAt());
  }
}
