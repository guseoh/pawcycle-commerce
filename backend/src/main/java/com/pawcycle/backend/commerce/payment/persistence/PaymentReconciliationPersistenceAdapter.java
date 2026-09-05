package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentReconciliationPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public PaymentReconciliationPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public ReconciliationWork findForStart(long paymentId) {
    return queries
        .query(
            "SELECT id,type,status,provider_order_id AS providerOrderId,reconciliation_attempts AS reconciliationAttempts FROM payments WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ReconciliationWork(
                    rs.getLong("id"),
                    rs.getString("type"),
                    rs.getString("status"),
                    rs.getString("providerOrderId"),
                    rs.getInt("reconciliationAttempts")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void incrementAttempts(long paymentId, int attempts) {
    queries.update(
        "UPDATE payments SET reconciliation_attempts=?,last_reconciled_at=? WHERE id=?",
        attempts,
        now(),
        paymentId);
  }

  public ReconciliationTarget findForCompletion(long paymentId) {
    return queries
        .query(
            "SELECT payment.id,payment.order_id AS orderId,payment.type,payment.status,payment.reconciliation_attempts AS reconciliationAttempts,orders.member_id AS memberId,orders.source FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ReconciliationTarget(
                    rs.getLong("id"),
                    rs.getLong("orderId"),
                    rs.getString("type"),
                    rs.getString("status"),
                    rs.getInt("reconciliationAttempts"),
                    rs.getLong("memberId"),
                    rs.getString("source")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<OrderItem> findOrderItems(long orderId) {
    return queries.query(
        "SELECT sku_id AS skuId,quantity FROM order_items WHERE order_id=? ORDER BY sku_id",
        (rs, rowNumber) -> new OrderItem(rs.getLong("skuId"), rs.getInt("quantity")),
        orderId);
  }

  public void markSucceeded(long paymentId, String providerStatus, Timestamp paidAt) {
    queries.update(
        "UPDATE payments SET status='SUCCEEDED',provider_status=?,approved_at=? WHERE id=?",
        providerStatus,
        paidAt,
        paymentId);
  }

  public void markFailed(long paymentId, String providerStatus) {
    queries.update(
        "UPDATE payments SET status='FAILED',provider_status=?,failure_code='RECONCILED_FAILED',failed_at=? WHERE id=?",
        providerStatus,
        now(),
        paymentId);
  }

  public void markOrderPaid(long orderId, Timestamp paidAt) {
    queries.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", paidAt, orderId);
  }

  public void markOrderPaymentFailed(long orderId) {
    queries.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", orderId);
  }

  public void markOrderActionRequired(long orderId) {
    queries.update("UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=?", orderId);
  }

  public void useReservedCoupon(long orderId, Timestamp paidAt) {
    queries.update(
        "UPDATE member_coupons SET status='USED',used_at=? WHERE reserved_order_id=? AND status='RESERVED'",
        paidAt,
        orderId);
  }

  public void releaseReservedCoupon(long orderId) {
    queries.update(
        "UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'",
        orderId);
  }

  public void consumeCart(long memberId, long orderId) {
    Long cartId =
        queries.query("SELECT id FROM carts WHERE member_id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
    if (cartId == null) return;
    for (OrderItem item : findOrderItems(orderId)) {
      Integer current =
          queries.query(
              "SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
              (rs, rowNumber) -> rs.next() ? rs.getInt(1) : null,
              cartId,
              item.skuId()).stream().findFirst().orElse(null);
      if (current == null) continue;
      if (current <= item.quantity()) {
        queries.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cartId, item.skuId());
      } else {
        queries.update("UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?", current - item.quantity(), cartId, item.skuId());
      }
    }
    queries.update("UPDATE carts SET updated_at=? WHERE id=?", now(), cartId);
  }

  public PaymentReconciliationView find(long paymentId) {
    return queries
        .query(
            "SELECT id AS paymentId,order_id AS orderId,status,reconciliation_attempts AS reconciliationAttempts,last_reconciled_at AS lastReconciledAt FROM payments WHERE id=?",
            (rs, rowNumber) ->
                new PaymentReconciliationView(
                    rs.getLong("paymentId"),
                    rs.getLong("orderId"),
                    rs.getString("status"),
                    rs.getInt("reconciliationAttempts"),
                    rs.getTimestamp("lastReconciledAt")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record ReconciliationWork(long paymentId, String type, String status, String providerOrderId, int attempts) {}
  public record ReconciliationTarget(long paymentId, long orderId, String type, String status, int attempts, long memberId, String source) {}
  public record OrderItem(long skuId, int quantity) {}
  public record PaymentReconciliationView(long paymentId, long orderId, String status, int attempts, Timestamp lastReconciledAt) {}
}
