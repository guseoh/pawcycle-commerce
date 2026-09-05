package com.pawcycle.backend.commerce.order.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

public record OrderView(
    long orderId,
    String orderNumber,
    String source,
    String status,
    BigDecimal originalAmount,
    BigDecimal discountAmount,
    BigDecimal shippingFee,
    BigDecimal paymentAmount,
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2,
    Timestamp createdAt,
    Timestamp paidAt,
    List<Item> items,
    Payment payment,
    Delivery delivery,
    Cancellation cancellation,
    ReturnRequest returnRequest,
    List<Refund> refunds) {

  public record Item(
      long skuId,
      String snapshotQuality,
      String skuCodeSnapshot,
      String productNameSnapshot,
      String skuNameSnapshot,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal lineAmount) {}

  public record Payment(
      long paymentId,
      String type,
      String provider,
      String status,
      BigDecimal amount,
      int attemptNo,
      String providerStatus) {}

  public record Delivery(
      long deliveryId,
      long orderId,
      String status,
      String carrierCode,
      String trackingNumber,
      String failureReason,
      Timestamp shippedAt,
      Timestamp deliveredAt,
      Timestamp failedAt,
      Timestamp cancelledAt) {}

  public record Cancellation(
      long cancellationId,
      String status,
      String reason,
      Timestamp requestedAt,
      Timestamp completedAt) {}

  public record ReturnRequest(
      long returnId,
      String status,
      String reason,
      String rejectionReason,
      Boolean restock,
      Timestamp requestedAt,
      Timestamp receivedAt,
      Timestamp completedAt) {}

  public record Refund(
      long refundId,
      String source,
      String status,
      BigDecimal amount,
      int attemptNo,
      int reconciliationAttempts) {}
}
