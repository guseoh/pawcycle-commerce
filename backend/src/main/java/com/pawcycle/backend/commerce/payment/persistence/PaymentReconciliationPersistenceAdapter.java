package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.commerce.payment.domain.QPaymentEntity;
import com.pawcycle.backend.commerce.coupon.domain.QMemberCouponEntity;
import com.pawcycle.backend.commerce.cart.domain.QCartEntity;
import com.pawcycle.backend.commerce.cart.domain.QCartItemEntity;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentReconciliationPersistenceAdapter {
  private final PaymentNativeLocks locks;
  private final PaymentPersistenceQueries writes;
  private final JPAQueryFactory queries;
  private final Clock clock;

  public PaymentReconciliationPersistenceAdapter(EntityManager entities, Clock clock) {
    this.locks = new PaymentNativeLocks(entities);
    this.writes = new PaymentPersistenceQueries(entities);
    this.queries = new JPAQueryFactory(entities);
    this.clock = clock;
  }

  public ReconciliationWork findForStart(long paymentId) {
    return locks.first("SELECT id,type,status,provider_order_id,reconciliation_attempts FROM payments WHERE id=? FOR UPDATE",
        ReconciliationWork.class, paymentId);
  }

  public void incrementAttempts(long paymentId, int attempts) {
    writes.reconciliation(attempts, now(), paymentId);
  }

  public ReconciliationTarget findForCompletion(long paymentId) {
    return locks.first("""
        SELECT payment.id,payment.order_id,payment.type,payment.status,payment.reconciliation_attempts,orders.member_id,orders.source
        FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=? FOR UPDATE
        """, ReconciliationTarget.class, paymentId);
  }

  public List<OrderItem> findOrderItems(long orderId) {
    return writes.items(orderId, OrderItem.class, true);
  }

  public void markSucceeded(long paymentId, String providerStatus, Timestamp paidAt) {
    writes.succeeded(providerStatus, paidAt, paymentId);
  }

  public void markFailed(long paymentId, String providerStatus) {
    writes.failed(providerStatus, "RECONCILED_FAILED", now(), paymentId);
  }

  public void markOrderPaid(long orderId, Timestamp paidAt) {
    writes.orderPaid(paidAt, orderId);
  }

  public void markOrderPaymentFailed(long orderId) {
    writes.orderStatus("PAYMENT_FAILED", orderId);
  }

  public void markOrderActionRequired(long orderId) {
    writes.orderStatus("PAYMENT_ACTION_REQUIRED", orderId);
  }

  public void useReservedCoupon(long orderId, Timestamp paidAt) {
    var coupon = new QMemberCouponEntity("coupon");
    writes.mutate(() -> queries.update(coupon).set(coupon.status, "USED")
        .set(coupon.usedAt, PaymentPersistenceQueries.jdbcTime(paidAt))
        .where(coupon.reservedOrderId.eq(orderId), coupon.status.eq("RESERVED")).execute());
  }

  public void releaseReservedCoupon(long orderId) {
    var coupon = new QMemberCouponEntity("coupon");
    writes.mutate(() -> queries.update(coupon).set(coupon.status, "AVAILABLE").setNull(coupon.reservedOrderId)
        .where(coupon.reservedOrderId.eq(orderId), coupon.status.eq("RESERVED")).execute());
  }

  public void consumeCart(long memberId, long orderId) {
    Long cartId = locks.first("SELECT id FROM carts WHERE member_id=? FOR UPDATE", Long.class, memberId);
    if (cartId == null) return;
    var cartItem = new QCartItemEntity("cartItem");
    for (OrderItem item : findOrderItems(orderId)) {
      Integer current = locks.first("SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
          Integer.class, cartId, item.skuId());
      if (current == null) continue;
      var predicate = cartItem.id.cartId.eq(cartId).and(cartItem.id.skuId.eq(item.skuId()));
      if (current <= item.quantity()) {
        writes.mutate(() -> queries.delete(cartItem).where(predicate).execute());
      } else {
        writes.mutate(() -> queries.update(cartItem).set(cartItem.quantity, current - item.quantity()).where(predicate).execute());
      }
    }
    var cart = new QCartEntity("cart");
    writes.mutate(() -> queries.update(cart).set(cart.updatedAt, PaymentPersistenceQueries.jdbcTime(now()))
        .where(cart.id.eq(cartId)).execute());
  }

  public PaymentReconciliationView find(long paymentId) {
    var payment = new QPaymentEntity("payment");
    // Read MySQL's DATETIME wall clock as text: legacy getTimestamp() had no UTC Calendar.
    var wallClock = Expressions.stringTemplate("cast({0} as string)", payment.lastReconciledAt);
    var row = queries.select(payment.id, payment.orderId, payment.status, payment.reconciliationAttempts, wallClock)
        .from(payment).where(payment.id.eq(paymentId)).fetchOne();
    if (row == null) return null;
    String time = row.get(wallClock);
    return new PaymentReconciliationView(row.get(payment.id), row.get(payment.orderId), row.get(payment.status),
        row.get(payment.reconciliationAttempts), time == null ? null : Timestamp.valueOf(time));
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record ReconciliationWork(long paymentId, String type, String status, String providerOrderId, int attempts) {}
  public record ReconciliationTarget(long paymentId, long orderId, String type, String status, int attempts, long memberId, String source) {}
  public record OrderItem(long skuId, int quantity) {}
  public record PaymentReconciliationView(long paymentId, long orderId, String status, int attempts, Timestamp lastReconciledAt) {}
}
