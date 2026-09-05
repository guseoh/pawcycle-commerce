package com.pawcycle.backend.commerce.checkout.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutExpirationPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public CheckoutExpirationPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public List<Long> findDuePaymentIds(int batchSize) {
    return queries.queryForList(
        "SELECT id FROM payments WHERE type='NORMAL' AND status='READY' AND expires_at IS NOT NULL AND expires_at<=? ORDER BY expires_at,id LIMIT ?",
        Long.class,
        Timestamp.from(clock.instant()),
        batchSize);
  }

  public ExpirationTarget findForUpdate(long paymentId) {
    return queries
        .query(
            "SELECT payment.id AS paymentId,payment.order_id AS orderId,payment.status,orders.status AS orderStatus FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ExpirationTarget(
                    rs.getLong("paymentId"),
                    rs.getLong("orderId"),
                    rs.getString("status"),
                    rs.getString("orderStatus")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<CheckoutCartItem> findOrderItems(long orderId) {
    return queries.query(
        "SELECT sku_id AS skuId,quantity FROM order_items WHERE order_id=?",
        (rs, rowNumber) -> new CheckoutCartItem(rs.getLong("skuId"), rs.getInt("quantity")),
        orderId);
  }

  public void releaseCoupon(long orderId) {
    queries.update(
        "UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'",
        orderId);
  }

  public void markExpired(long paymentId, long orderId) {
    queries.update(
        "UPDATE payments SET status='FAILED',provider_status='EXPIRED',failure_code='CHECKOUT_EXPIRED',failed_at=? WHERE id=?",
        Timestamp.from(clock.instant()),
        paymentId);
    queries.update("UPDATE orders SET status='EXPIRED' WHERE id=?", orderId);
  }

  public record ExpirationTarget(long paymentId, long orderId, String paymentStatus, String orderStatus) {}
  public record CheckoutCartItem(long skuId, int quantity) {}
}
