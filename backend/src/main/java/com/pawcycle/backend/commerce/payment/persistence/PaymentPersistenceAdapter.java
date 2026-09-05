package com.pawcycle.backend.commerce.payment.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public PaymentPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public PaymentWork findByProviderOrderIdForUpdate(String providerOrderId) {
    return queries
        .query(
            "SELECT payment.id AS paymentId,payment.order_id AS orderId,payment.amount,payment.status,payment.payment_key AS paymentKey,orders.member_id AS memberId,orders.status AS orderStatus FROM payments payment JOIN orders ON orders.id=payment.order_id WHERE payment.provider_order_id=? FOR UPDATE",
            (rs, rowNumber) ->
                new PaymentWork(
                    rs.getLong("paymentId"),
                    rs.getLong("orderId"),
                    rs.getBigDecimal("amount"),
                    rs.getString("status"),
                    rs.getString("paymentKey"),
                    rs.getLong("memberId"),
                    rs.getString("orderStatus")),
            providerOrderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void markProcessing(long paymentId, String paymentKey) {
    queries.update(
        "UPDATE payments SET status='PROCESSING',payment_key=?,provider_status='REQUESTED' WHERE id=?",
        paymentKey,
        paymentId);
  }

  public PaymentState findForUpdate(long paymentId) {
    return queries
        .query(
            "SELECT id AS paymentId,order_id AS orderId,status FROM payments WHERE id=? FOR UPDATE",
            (rs, rowNumber) ->
                new PaymentState(rs.getLong("paymentId"), rs.getLong("orderId"), rs.getString("status")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<OrderItem> findOrderItems(long orderId) {
    return queries.query(
        "SELECT sku_id AS skuId,quantity FROM order_items WHERE order_id=?",
        (rs, rowNumber) -> new OrderItem(rs.getLong("skuId"), rs.getInt("quantity")),
        orderId);
  }

  public void markSucceeded(long paymentId, String paymentKey) {
    queries.update(
        "UPDATE payments SET status='SUCCEEDED',provider_status='DONE',payment_key=?,approved_at=? WHERE id=?",
        paymentKey,
        now(),
        paymentId);
  }

  public void markFailed(long paymentId) {
    queries.update(
        "UPDATE payments SET status='FAILED',provider_status='ABORTED',failure_code='TOSS_REJECTED',failed_at=? WHERE id=?",
        now(),
        paymentId);
  }

  public void markUnknown(long paymentId) {
    queries.update(
        "UPDATE payments SET status='UNKNOWN',provider_status='UNKNOWN',failure_code='PROVIDER_RESULT_UNKNOWN' WHERE id=?",
        paymentId);
  }

  public void markOrderPaid(long orderId) {
    queries.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", now(), orderId);
  }

  public void markOrderPaymentFailed(long orderId) {
    queries.update("UPDATE orders SET status='PAYMENT_FAILED' WHERE id=?", orderId);
  }

  public long findMemberId(long orderId) {
    return queries.queryForObject("SELECT member_id FROM orders WHERE id=?", Long.class, orderId);
  }

  public void useReservedCoupon(long orderId) {
    queries.update(
        "UPDATE member_coupons SET status='USED',used_at=? WHERE reserved_order_id=? AND status='RESERVED'",
        now(),
        orderId);
  }

  public void releaseReservedCoupon(long orderId) {
    queries.update(
        "UPDATE member_coupons SET status='AVAILABLE',reserved_order_id=NULL WHERE reserved_order_id=? AND status='RESERVED'",
        orderId);
  }

  public void consumeCart(long memberId, long orderId) {
    CartLock cart = lockCart(memberId);
    boolean changed = false;
    for (OrderItem item : findOrderItems(orderId)) {
      Integer current =
          queries.query(
              "SELECT quantity FROM cart_items WHERE cart_id=? AND sku_id=? FOR UPDATE",
              (rs, rowNumber) -> rs.next() ? rs.getInt(1) : null,
              cart.id(),
              item.skuId()).stream().findFirst().orElse(null);
      if (current == null) continue;
      if (current <= item.quantity()) {
        queries.update("DELETE FROM cart_items WHERE cart_id=? AND sku_id=?", cart.id(), item.skuId());
      } else {
        queries.update(
            "UPDATE cart_items SET quantity=? WHERE cart_id=? AND sku_id=?",
            current - item.quantity(),
            cart.id(),
            item.skuId());
      }
      changed = true;
    }
    if (changed) queries.update("UPDATE carts SET version=version+1,updated_at=? WHERE id=?", now(), cart.id());
  }

  private CartLock lockCart(long memberId) {
    Long cartId =
        queries.query("SELECT id FROM carts WHERE member_id=? FOR UPDATE", rs -> rs.next() ? rs.getLong(1) : null, memberId);
    if (cartId == null) {
      queries.update("INSERT INTO carts(member_id,created_at,updated_at) VALUES (?,?,?)", memberId, now(), now());
      cartId = queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    return new CartLock(cartId);
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record PaymentWork(long paymentId, long orderId, java.math.BigDecimal amount, String status, String paymentKey, long memberId, String orderStatus) {}
  public record PaymentState(long paymentId, long orderId, String status) {}
  public record OrderItem(long skuId, int quantity) {}
  private record CartLock(long id) {}
}
