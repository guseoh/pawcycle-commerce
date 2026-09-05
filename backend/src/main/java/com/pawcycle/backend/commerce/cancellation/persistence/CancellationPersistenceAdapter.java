package com.pawcycle.backend.commerce.cancellation.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public CancellationPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
    this.queries = queries;
    this.clock = clock;
  }

  public OrderLock findOrderForUpdate(long memberId, long orderId) {
    return queries
        .query(
            "SELECT id,status FROM orders WHERE id=? AND member_id=? FOR UPDATE",
            (rs, rowNumber) -> new OrderLock(rs.getLong("id"), rs.getString("status")),
            orderId,
            memberId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public CancellationView findExisting(long orderId) {
    return queries
        .query(
            "SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE order_id=? FOR UPDATE",
            (rs, rowNumber) ->
                new CancellationView(
                    rs.getLong("cancellationId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("completedAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public DeliveryLock findDeliveryForUpdate(long orderId) {
    return queries
        .query(
            "SELECT id,status FROM deliveries WHERE order_id=? FOR UPDATE",
            (rs, rowNumber) -> new DeliveryLock(rs.getLong("id"), rs.getString("status")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean hasReturnForUpdate(long orderId) {
    return !queries
        .query("SELECT id FROM order_returns WHERE order_id=? FOR UPDATE", (rs, rowNumber) -> rs.getLong(1), orderId)
        .isEmpty();
  }

  public boolean hasSuccessfulPayment(long orderId) {
    return !queries
        .query(
            "SELECT id FROM payments WHERE order_id=? AND status='SUCCEEDED' FOR UPDATE",
            (rs, rowNumber) -> rs.getLong(1),
            orderId)
        .isEmpty();
  }

  public long create(long orderId, String reason) {
    Timestamp now = now();
    queries.update(
        "INSERT INTO order_cancellations(order_id,status,reason,requested_at) VALUES (?,'REFUND_PENDING',?,?)",
        orderId,
        reason,
        now);
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public void cancelDelivery(long deliveryId) {
    queries.update("UPDATE deliveries SET status='CANCELLED',cancelled_at=? WHERE id=?", now(), deliveryId);
  }

  public void createRefund(long orderId, long cancellationId) {
    queries.update(
        "INSERT INTO refunds(order_id,source,cancellation_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?,'CANCELLATION',?,'READY',payment_amount,'TOSS',?,1,? FROM orders WHERE id=?",
        orderId,
        cancellationId,
        "refund-" + UUID.randomUUID(),
        now(),
        orderId);
  }

  public CancellationView find(long cancellationId) {
    return queries
        .query(
            "SELECT id AS cancellationId,status,reason,requested_at AS requestedAt,completed_at AS completedAt FROM order_cancellations WHERE id=?",
            (rs, rowNumber) ->
                new CancellationView(
                    rs.getLong("cancellationId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("completedAt")),
            cancellationId)
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

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record OrderLock(long orderId, String status) {}
  public record DeliveryLock(long deliveryId, String status) {}
  public record OrderItem(long skuId, int quantity) {}
  public record CancellationView(long cancellationId, String status, String reason, Timestamp requestedAt, Timestamp completedAt) {}
}
