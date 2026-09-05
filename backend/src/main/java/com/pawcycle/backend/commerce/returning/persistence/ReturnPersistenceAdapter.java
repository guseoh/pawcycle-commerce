package com.pawcycle.backend.commerce.returning.persistence;

import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReturnPersistenceAdapter {
  private final NativeQueryExecutor queries;
  private final Clock clock;

  public ReturnPersistenceAdapter(NativeQueryExecutor queries, Clock clock) {
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

  public ReturnView findByOrderForUpdate(long orderId) {
    return queries
        .query(
            "SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,decided_at AS decidedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE order_id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ReturnView(
                    rs.getLong("returnId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("rejectionReason"),
                    nullableBoolean(rs, "restock"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("decidedAt"),
                    rs.getTimestamp("receivedAt"),
                    rs.getTimestamp("completedAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public boolean hasCancellationForUpdate(long orderId) {
    return !queries
        .query("SELECT id FROM order_cancellations WHERE order_id=? FOR UPDATE", (rs, rowNumber) -> rs.getLong(1), orderId)
        .isEmpty();
  }

  public DeliveryView findDeliveryForUpdate(long orderId) {
    return queries
        .query(
            "SELECT status,delivered_at AS deliveredAt FROM deliveries WHERE order_id=? FOR UPDATE",
            (rs, rowNumber) -> new DeliveryView(rs.getString("status"), rs.getTimestamp("deliveredAt")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public long create(long orderId, String reason) {
    queries.update(
        "INSERT INTO order_returns(order_id,status,reason,requested_at) VALUES (?,'REQUESTED',?,?)",
        orderId,
        reason,
        now());
    return queries.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public ReturnView find(long returnId) {
    return queries
        .query(
            "SELECT id AS returnId,status,reason,rejection_reason AS rejectionReason,restock,requested_at AS requestedAt,decided_at AS decidedAt,received_at AS receivedAt,completed_at AS completedAt FROM order_returns WHERE id=?",
            (rs, rowNumber) ->
                new ReturnView(
                    rs.getLong("returnId"),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("rejectionReason"),
                    nullableBoolean(rs, "restock"),
                    rs.getTimestamp("requestedAt"),
                    rs.getTimestamp("decidedAt"),
                    rs.getTimestamp("receivedAt"),
                    rs.getTimestamp("completedAt")),
            returnId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public ReturnDecision findForDecision(long returnId) {
    return queries
        .query(
            "SELECT return_order.id AS returnId,return_order.order_id AS orderId,return_order.status,orders.member_id AS memberId FROM order_returns return_order JOIN orders ON orders.id=return_order.order_id WHERE return_order.id=? FOR UPDATE",
            (rs, rowNumber) ->
                new ReturnDecision(
                    rs.getLong("returnId"),
                    rs.getLong("orderId"),
                    rs.getString("status"),
                    rs.getLong("memberId")),
            returnId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void decide(long returnId, String state, String reason, long adminId) {
    queries.update(
        "UPDATE order_returns SET status=?,rejection_reason=?,decided_at=?,decided_by_admin_id=? WHERE id=?",
        state,
        reason,
        now(),
        adminId,
        returnId);
  }

  public ReceiveTarget findForReceive(long returnId) {
    return queries
        .query(
            "SELECT id,order_id AS orderId,status FROM order_returns WHERE id=? FOR UPDATE",
            (rs, rowNumber) -> new ReceiveTarget(rs.getLong("id"), rs.getLong("orderId"), rs.getString("status")),
            returnId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void receive(long returnId, boolean restock, long adminId) {
    queries.update(
        "UPDATE order_returns SET status='REFUND_PENDING',restock=?,received_at=?,received_by_admin_id=? WHERE id=?",
        restock,
        now(),
        adminId,
        returnId);
  }

  public void createRefund(long orderId, long returnId) {
    queries.update(
        "INSERT INTO refunds(order_id,source,return_id,status,amount,provider,idempotency_key,attempt_no,requested_at) SELECT ?,'RETURN',?,'READY',payment_amount,'TOSS',?,1,? FROM orders WHERE id=?",
        orderId,
        returnId,
        "refund-" + UUID.randomUUID(),
        now(),
        orderId);
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

  private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    boolean value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }

  public record OrderLock(long orderId, String status) {}
  public record DeliveryView(String status, Timestamp deliveredAt) {}
  public record ReturnDecision(long returnId, long orderId, String status, long memberId) {}
  public record ReceiveTarget(long returnId, long orderId, String status) {}
  public record OrderItem(long skuId, int quantity) {}
  public record ReturnView(long returnId, String status, String reason, String rejectionReason, Boolean restock, Timestamp requestedAt, Timestamp decidedAt, Timestamp receivedAt, Timestamp completedAt) {}
}
