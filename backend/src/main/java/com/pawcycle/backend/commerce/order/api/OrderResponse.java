package com.pawcycle.backend.commerce.order.api;

import com.pawcycle.backend.commerce.order.model.OrderReadModel;
import com.pawcycle.backend.commerce.order.model.OrderView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

public record OrderResponse(
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
    List<Refund> refunds,
    List<String> availableActions) {

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
  public static OrderResponse from(OrderReadModel.Detail result) {
    OrderView view = result.view();
    List<String> actions = result.availableActions();
    return new OrderResponse(
        view.orderId(),
        view.orderNumber(),
        view.source(),
        view.status(),
        view.originalAmount(),
        view.discountAmount(),
        view.shippingFee(),
        view.paymentAmount(),
        view.recipientName(),
        view.recipientPhone(),
        view.postalCode(),
        view.addressLine1(),
        view.addressLine2(),
        view.createdAt(),
        view.paidAt(),
        view.items().stream()
            .map(
                item ->
                    new OrderResponse.Item(
                        item.skuId(),
                        item.snapshotQuality(),
                        item.skuCodeSnapshot(),
                        item.productNameSnapshot(),
                        item.skuNameSnapshot(),
                        item.unitPrice(),
                        item.quantity(),
                        item.lineAmount()))
            .toList(),
        payment(view.payment()),
        delivery(view.delivery()),
        cancellation(view.cancellation()),
        returnRequest(view.returnRequest()),
        view.refunds().stream()
            .map(
                refund ->
                    new OrderResponse.Refund(
                        refund.refundId(),
                        refund.source(),
                        refund.status(),
                        refund.amount(),
                        refund.attemptNo(),
                        refund.reconciliationAttempts()))
            .toList(),
        actions);
  }

  private static OrderResponse.Payment payment(OrderView.Payment payment) {
    return payment == null
        ? null
        : new OrderResponse.Payment(
            payment.paymentId(),
            payment.type(),
            payment.provider(),
            payment.status(),
            payment.amount(),
            payment.attemptNo(),
            payment.providerStatus());
  }

  private static OrderResponse.Delivery delivery(OrderView.Delivery delivery) {
    return delivery == null
        ? null
        : new OrderResponse.Delivery(
            delivery.deliveryId(),
            delivery.orderId(),
            delivery.status(),
            delivery.carrierCode(),
            delivery.trackingNumber(),
            delivery.failureReason(),
            delivery.shippedAt(),
            delivery.deliveredAt(),
            delivery.failedAt(),
            delivery.cancelledAt());
  }

  private static OrderResponse.Cancellation cancellation(OrderView.Cancellation cancellation) {
    return cancellation == null
        ? null
        : new OrderResponse.Cancellation(
            cancellation.cancellationId(),
            cancellation.status(),
            cancellation.reason(),
            cancellation.requestedAt(),
            cancellation.completedAt());
  }

  private static OrderResponse.ReturnRequest returnRequest(OrderView.ReturnRequest request) {
    return request == null
        ? null
        : new OrderResponse.ReturnRequest(
            request.returnId(),
            request.status(),
            request.reason(),
            request.rejectionReason(),
            request.restock(),
            request.requestedAt(),
            request.receivedAt(),
            request.completedAt());
  }
}
