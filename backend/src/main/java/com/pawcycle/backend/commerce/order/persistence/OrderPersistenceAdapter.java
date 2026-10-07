package com.pawcycle.backend.commerce.order.persistence;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPersistenceAdapter {
  private final EntityManager queries;

  public OrderPersistenceAdapter(EntityManager queries) {
    this.queries = queries;
  }

  public List<Summary> findOrders(long memberId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderReadRows$Summary(
            o.id, o.orderNumber, o.source, o.status, o.paymentAmount, o.createdAt, o.paidAt)
        from CommerceOrderEntity o where o.memberId = :memberId order by o.id desc
        """, OrderReadRows.Summary.class)
        .setParameter("memberId", memberId)
        .getResultList().stream().map(OrderReadRows.Summary::toView).toList();
  }

  public OrderView findOrder(long memberId, long orderId) {
    OrderReadRows.Header header = queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderReadRows$Header(
            o.id, o.orderNumber, o.source, o.status, o.originalAmount, o.discountAmount,
            o.shippingFee, o.paymentAmount, o.recipientName, o.recipientPhone, o.postalCode,
            o.addressLine1, o.addressLine2, o.createdAt, o.paidAt)
        from CommerceOrderEntity o where o.id = :orderId and o.memberId = :memberId
        """, OrderReadRows.Header.class)
        .setParameter("orderId", orderId).setParameter("memberId", memberId)
        .getResultList().stream().findFirst().orElse(null);
    if (header == null) return null;
    return new OrderView(
        header.orderId(), header.orderNumber(), header.source(), header.status(),
        header.originalAmount(), header.discountAmount(), header.shippingFee(), header.paymentAmount(),
        header.recipientName(), header.recipientPhone(), header.postalCode(), header.addressLine1(),
        header.addressLine2(), header.createdTimestamp(), header.paidTimestamp(), findItems(orderId),
        findPayment(orderId), findDelivery(orderId), findCancellation(orderId), findReturn(orderId), findRefunds(orderId));
  }

  private List<OrderView.Item> findItems(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderView$Item(
            i.skuId, i.snapshotQuality, i.skuCodeSnapshot, i.productNameSnapshot, i.skuNameSnapshot,
            i.unitPrice, i.quantity, i.lineAmount)
        from CommerceOrderItemEntity i where i.orderId = :orderId order by i.id
        """, OrderView.Item.class)
        .setParameter("orderId", orderId).getResultList();
  }

  private OrderView.Payment findPayment(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderView$Payment(
            p.id, p.type, p.provider, p.status, p.amount, p.attemptNo, p.providerStatus)
        from PaymentEntity p where p.orderId = :orderId order by p.attemptNo desc
        """, OrderView.Payment.class)
        .setParameter("orderId", orderId).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
  }

  private OrderView.Delivery findDelivery(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderReadRows$Delivery(
            d.id, d.orderId, d.status, d.carrierCode, d.trackingNumber, d.failureReason,
            d.shippedAt, d.deliveredAt, d.failedAt, d.cancelledAt)
        from DeliveryEntity d where d.orderId = :orderId
        """, OrderReadRows.Delivery.class)
        .setParameter("orderId", orderId).getResultList().stream().findFirst()
        .map(OrderReadRows.Delivery::toView).orElse(null);
  }

  private OrderView.Cancellation findCancellation(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderReadRows$Cancellation(
            c.id, c.status, c.reason, c.requestedAt, c.completedAt)
        from OrderCancellationEntity c where c.orderId = :orderId
        """, OrderReadRows.Cancellation.class)
        .setParameter("orderId", orderId).getResultList().stream().findFirst()
        .map(OrderReadRows.Cancellation::toView).orElse(null);
  }

  private OrderView.ReturnRequest findReturn(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderReadRows$ReturnRequest(
            r.id, r.status, r.reason, r.rejectionReason, r.restock, r.requestedAt, r.receivedAt, r.completedAt)
        from OrderReturnEntity r where r.orderId = :orderId
        """, OrderReadRows.ReturnRequest.class)
        .setParameter("orderId", orderId).getResultList().stream().findFirst()
        .map(OrderReadRows.ReturnRequest::toView).orElse(null);
  }

  private List<OrderView.Refund> findRefunds(long orderId) {
    return queries.createQuery("""
        select new com.pawcycle.backend.commerce.order.persistence.OrderView$Refund(
            r.id, r.source, r.status, r.amount, r.attemptNo, r.reconciliationAttempts)
        from RefundEntity r where r.orderId = :orderId order by r.attemptNo
        """, OrderView.Refund.class)
        .setParameter("orderId", orderId).getResultList();
  }

  public record Summary(
      long orderId,
      String orderNumber,
      String source,
      String status,
      BigDecimal paymentAmount,
      Timestamp createdAt,
      Timestamp paidAt) {}
}
