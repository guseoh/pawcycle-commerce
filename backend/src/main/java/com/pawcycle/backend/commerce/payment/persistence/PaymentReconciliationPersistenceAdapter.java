package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.commerce.order.domain.QCommerceOrderEntity;
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

  /**
   * Eligibility facts for recovering an isolated NORMAL/PROCESSING payment (#417 Phase 2a).
   * The locking variant locks only the payment row ({@code FOR UPDATE OF payment}); the order row is
   * locked later by the order update, keeping the normal finalization order payment -> inventory ->
   * order.
   */
  public RecoveryCandidate findRecoveryCandidate(long paymentId, Timestamp expiredBefore, boolean forUpdate) {
    String sql = """
        SELECT payment.id,payment.order_id,payment.type,payment.status,orders.status AS order_status,
               orders.member_id,payment.provider_order_id,payment.payment_key,payment.amount,
               CAST(CASE WHEN payment.expires_at <= ? THEN 1 ELSE 0 END AS SIGNED) AS expired_long_enough,
               (SELECT COUNT(*) FROM order_items item WHERE item.order_id=payment.order_id) AS order_item_count,
               (SELECT COUNT(*) FROM inventory_movements movement
                 WHERE movement.payment_id=payment.id AND movement.type='RESERVE') AS reserve_count,
               (SELECT COUNT(*) FROM inventory_movements movement
                 WHERE movement.payment_id=payment.id AND movement.type IN ('DEDUCT','RELEASE')) AS settled_count
        FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=?
        """ + (forUpdate ? " FOR UPDATE OF payment" : "");
    return locks.first(sql, RecoveryCandidate.class, expiredBefore, paymentId);
  }

  /**
   * Eligibility facts for confirming the provider failure of an isolated NORMAL/PROCESSING payment
   * (#417 Phase 2b). Unlike {@link #findRecoveryCandidate} it compares the RESERVE ledger with the
   * order per SKU and quantity, and counts SUCCEEDED payments of the same order. The locking variant
   * locks only the payment row, so the confirmation keeps the order payment -> inventory -> order ->
   * coupon.
   */
  public FailureCandidate findFailureCandidate(long paymentId, Timestamp expiredBefore, boolean forUpdate) {
    String sql = """
        SELECT payment.id,payment.order_id,payment.type,payment.status,orders.status AS order_status,
               payment.provider_order_id,payment.payment_key,payment.amount,
               CAST(CASE WHEN payment.expires_at <= ? THEN 1 ELSE 0 END AS SIGNED) AS expired_long_enough,
               (SELECT COUNT(*) FROM order_items item WHERE item.order_id=payment.order_id) AS order_item_count,
               (SELECT COUNT(*) FROM inventory_movements movement
                 WHERE movement.payment_id=payment.id AND movement.type='RESERVE') AS reserve_count,
               (SELECT COUNT(*) FROM order_items item WHERE item.order_id=payment.order_id
                 AND NOT EXISTS (SELECT 1 FROM inventory_movements movement
                   WHERE movement.payment_id=payment.id AND movement.type='RESERVE'
                     AND movement.sku_id=item.sku_id AND movement.quantity=item.quantity)) AS reserve_mismatch_count,
               (SELECT COUNT(*) FROM inventory_movements movement
                 WHERE movement.payment_id=payment.id AND movement.type IN ('DEDUCT','RELEASE')) AS settled_count,
               (SELECT COUNT(*) FROM payments other
                 WHERE other.order_id=payment.order_id AND other.status='SUCCEEDED') AS succeeded_payment_count
        FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=?
        """ + (forUpdate ? " FOR UPDATE OF payment" : "");
    return locks.first(sql, FailureCandidate.class, expiredBefore, paymentId);
  }

  /**
   * Everything a failure confirmation commits, read together. Used to tell a committed confirmation
   * from a rolled-back one when the commit acknowledgement was lost (#417 Phase 2b).
   */
  public FailureEvidence findFailureEvidence(long paymentId, String failureCode, List<String> auditActions) {
    String sql = """
        SELECT payment.status,payment.provider_status,payment.failure_code,orders.status AS order_status,
               (SELECT COUNT(*) FROM order_items item WHERE item.order_id=payment.order_id) AS order_item_count,
               (SELECT COUNT(*) FROM inventory_movements movement
                 WHERE movement.payment_id=payment.id AND movement.type='RELEASE') AS release_count,
               (SELECT COUNT(*) FROM order_items item WHERE item.order_id=payment.order_id
                 AND NOT EXISTS (SELECT 1 FROM inventory_movements movement
                   WHERE movement.payment_id=payment.id AND movement.type='RELEASE'
                     AND movement.sku_id=item.sku_id AND movement.quantity=item.quantity)) AS release_mismatch_count,
               (SELECT COUNT(*) FROM member_coupons coupon
                 WHERE coupon.reserved_order_id=payment.order_id AND coupon.status='RESERVED') AS reserved_coupon_count,
               (SELECT COUNT(*) FROM admin_audit_logs audit
                 WHERE audit.target_type='PAYMENT' AND audit.target_id=payment.id AND audit.action IN (?,?)) AS audit_count
        FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=?
        """;
    return locks.first(sql, FailureEvidence.class, auditActions.get(0), auditActions.get(1), paymentId);
  }

  /** Payment PROCESSING -> FAILED, only while it is still PROCESSING. Returns the updated row count. */
  public int markProcessingFailed(long paymentId, String providerStatus, String failureCode) {
    var payment = new QPaymentEntity("payment");
    return writes.mutate(() -> queries.update(payment).set(payment.status, "FAILED")
        .set(payment.providerStatus, providerStatus).set(payment.failureCode, failureCode)
        .set(payment.failedAt, PaymentPersistenceQueries.jdbcTime(now()))
        .where(payment.id.eq(paymentId), payment.status.eq("PROCESSING")).execute());
  }

  /** Order PAYMENT_PENDING -> PAYMENT_FAILED, only while it is still PAYMENT_PENDING. */
  public int markPendingOrderPaymentFailed(long orderId) {
    var order = new QCommerceOrderEntity("orders");
    return writes.mutate(() -> queries.update(order).set(order.status, "PAYMENT_FAILED")
        .where(order.id.eq(orderId), order.status.eq("PAYMENT_PENDING")).execute());
  }

  public boolean isAdmin(long memberId) {
    Long count = locks.first("SELECT COUNT(*) FROM members WHERE id=? AND role='ADMIN'", Long.class, memberId);
    return count != null && count > 0;
  }

  /** Non-locking identity projection: observation never claims or completes a payment. */
  public QueryTarget findQueryTarget(long paymentId) {
    var payment = new QPaymentEntity("payment");
    var row = queries.select(payment.type, payment.providerOrderId, payment.paymentKey, payment.amount)
        .from(payment).where(payment.id.eq(paymentId)).fetchOne();
    return row == null ? null : new QueryTarget(row.get(payment.type), row.get(payment.providerOrderId),
        row.get(payment.paymentKey), row.get(payment.amount));
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
    boolean changed = false;
    for (OrderItem item : findOrderItems(orderId)) {
      Integer current = locks.first("SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
          Integer.class, cartId, item.skuId());
      if (current == null) continue;
      var predicate = cartItem.id.cartId.eq(cartId).and(cartItem.id.skuId.eq(item.skuId()));
      if (current <= item.quantity()) {
        changed |= writes.mutate(() -> queries.delete(cartItem).where(predicate).execute()) > 0;
      } else {
        changed |= writes.mutate(() -> queries.update(cartItem).set(cartItem.quantity, current - item.quantity()).where(predicate).execute()) > 0;
      }
    }
    var cart = new QCartEntity("cart");
    var update = queries.update(cart).set(cart.updatedAt, java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
    if (changed) update.set(cart.version, cart.version.add(1));
    writes.mutate(() -> update.where(cart.id.eq(cartId)).execute());
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

  public record RecoveryCandidate(
      Long paymentId,
      Long orderId,
      String type,
      String status,
      String orderStatus,
      Long memberId,
      String providerOrderId,
      String paymentKey,
      BigDecimal amount,
      Long expiredLongEnough,
      Long orderItemCount,
      Long reserveCount,
      Long settledCount) {}
  public record FailureCandidate(
      Long paymentId,
      Long orderId,
      String type,
      String status,
      String orderStatus,
      String providerOrderId,
      String paymentKey,
      BigDecimal amount,
      Long expiredLongEnough,
      Long orderItemCount,
      Long reserveCount,
      Long reserveMismatchCount,
      Long settledCount,
      Long succeededPaymentCount) {}
  public record FailureEvidence(
      String status,
      String providerStatus,
      String failureCode,
      String orderStatus,
      Long orderItemCount,
      Long releaseCount,
      Long releaseMismatchCount,
      Long reservedCouponCount,
      Long auditCount) {}
  public record QueryTarget(String type, String providerOrderId, String paymentKey, BigDecimal amount) {}
  public record ReconciliationWork(long paymentId, String type, String status, String providerOrderId, int attempts) {}
  public record ReconciliationTarget(long paymentId, long orderId, String type, String status, int attempts, long memberId, String source) {}
  public record OrderItem(long skuId, int quantity) {}
  public record PaymentReconciliationView(long paymentId, long orderId, String status, int attempts, Timestamp lastReconciledAt) {}
}
