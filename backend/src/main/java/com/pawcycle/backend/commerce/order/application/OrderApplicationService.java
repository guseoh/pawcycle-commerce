package com.pawcycle.backend.commerce.order.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.order.api.OrderReorderResponse;
import com.pawcycle.backend.commerce.order.api.OrderResponse;
import com.pawcycle.backend.commerce.order.api.OrderSummaryResponse;
import com.pawcycle.backend.commerce.order.persistence.OrderPersistenceAdapter;
import com.pawcycle.backend.commerce.order.persistence.OrderView;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderApplicationService {
  private final OrderPersistenceAdapter orders;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final int returnRequestDays;

  public OrderApplicationService(
      OrderPersistenceAdapter orders,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      Clock clock,
      @Value("${pawcycle.commerce.return-request-days:7}") int returnRequestDays) {
    this.orders = orders;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
    this.returnRequestDays = returnRequestDays;
  }

  public List<OrderSummaryResponse> orders(long memberId) {
    return orders.findOrders(memberId).stream()
        .map(
            view ->
                new OrderSummaryResponse(
                    view.orderId(),
                    view.orderNumber(),
                    view.source(),
                    view.status(),
                    view.paymentAmount(),
                    view.createdAt(),
                    view.paidAt()))
        .toList();
  }

  public OrderResponse order(long memberId, long orderId) {
    OrderView view = orders.findOrder(memberId, orderId);
    if (view == null) throw new CommerceException(404, "ORDER_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    return toResponse(view);
  }

  public OrderReorderResponse reorder(long memberId, long sourceOrderId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
      throw new CommerceException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key가 필요합니다.");
    }
    OrderPersistenceAdapter.ReorderResult result =
        transaction.execute(status -> orders.reorder(memberId, sourceOrderId, idempotencyKey));
    return new OrderReorderResponse(
        result.addedItems().stream()
            .map(item -> new OrderReorderResponse.Item(item.skuId(), item.quantity()))
            .toList(),
        result.skippedItems().stream()
            .map(item -> new OrderReorderResponse.SkippedItem(item.skuId(), item.quantity(), item.reason()))
            .toList(),
        result.cartVersion());
  }

  private OrderResponse toResponse(OrderView view) {
    List<String> actions = new ArrayList<>();
    OrderView.Delivery delivery = view.delivery();
    if ("PAID".equals(view.status())
        && delivery != null
        && "PREPARING".equals(delivery.status())
        && view.cancellation() == null) {
      actions.add("REQUEST_CANCELLATION");
    }
    Timestamp deliveredAt = delivery == null ? null : delivery.deliveredAt();
    boolean returnWindowOpen =
        deliveredAt != null
            && !deliveredAt.toInstant()
                .plus(returnRequestDays, ChronoUnit.DAYS)
                .isBefore(clock.instant());
    if (delivery != null
        && "DELIVERED".equals(delivery.status())
        && returnWindowOpen
        && view.returnRequest() == null) {
      actions.add("REQUEST_RETURN");
    }
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
