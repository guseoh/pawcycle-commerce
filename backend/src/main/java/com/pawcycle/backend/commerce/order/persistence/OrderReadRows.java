package com.pawcycle.backend.commerce.order.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class OrderReadRows {
  private OrderReadRows() {}

  // Hibernate reads TIMESTAMP with the existing UTC JDBC calendar, then wraps it in JVM-local
  // LocalDateTime. Recover the DATETIME wall value before applying the legacy getTimestamp zone.
  private static Timestamp timestamp(LocalDateTime value) {
    if (value == null) return null;
    LocalDateTime stored = LocalDateTime.ofInstant(Timestamp.valueOf(value).toInstant(), ZoneOffset.UTC);
    return Timestamp.valueOf(stored);
  }

  record Summary(
      long orderId, String orderNumber, String source, String status, BigDecimal paymentAmount,
      LocalDateTime createdAt, LocalDateTime paidAt) {
    OrderPersistenceAdapter.Summary toView() {
      return new OrderPersistenceAdapter.Summary(
          orderId, orderNumber, source, status, paymentAmount, timestamp(createdAt), timestamp(paidAt));
    }
  }

  record Header(
      long orderId, String orderNumber, String source, String status, BigDecimal originalAmount,
      BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal paymentAmount,
      String recipientName, String recipientPhone, String postalCode, String addressLine1,
      String addressLine2, LocalDateTime createdAt, LocalDateTime paidAt) {
    Timestamp createdTimestamp() { return timestamp(createdAt); }
    Timestamp paidTimestamp() { return timestamp(paidAt); }
  }

  record Delivery(
      long deliveryId, long orderId, String status, String carrierCode, String trackingNumber,
      String failureReason, LocalDateTime shippedAt, LocalDateTime deliveredAt,
      LocalDateTime failedAt, LocalDateTime cancelledAt) {
    OrderView.Delivery toView() {
      return new OrderView.Delivery(deliveryId, orderId, status, carrierCode, trackingNumber,
          failureReason, timestamp(shippedAt), timestamp(deliveredAt), timestamp(failedAt), timestamp(cancelledAt));
    }
  }

  record Cancellation(
      long cancellationId, String status, String reason, LocalDateTime requestedAt,
      LocalDateTime completedAt) {
    OrderView.Cancellation toView() {
      return new OrderView.Cancellation(cancellationId, status, reason, timestamp(requestedAt), timestamp(completedAt));
    }
  }

  record ReturnRequest(
      long returnId, String status, String reason, String rejectionReason, Boolean restock,
      LocalDateTime requestedAt, LocalDateTime receivedAt, LocalDateTime completedAt) {
    OrderView.ReturnRequest toView() {
      return new OrderView.ReturnRequest(returnId, status, reason, rejectionReason, restock,
          timestamp(requestedAt), timestamp(receivedAt), timestamp(completedAt));
    }
  }
}
