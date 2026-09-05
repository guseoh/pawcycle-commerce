package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionBillingRetryPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionBillingRetryPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public InventoryState lockInventory(long skuId) {
    return jdbc
        .query(
            "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?"
                + " FOR UPDATE",
            (rs, row) ->
                new InventoryState(
                    rs.getInt("available_quantity"),
                    rs.getInt("reserved_quantity"),
                    rs.getLong("version")),
            skuId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<OrderItem> findOrderedItems(long orderId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id",
        (rs, row) -> new OrderItem(rs.getLong("sku_id"), rs.getInt("quantity")),
        orderId);
  }

  public List<OrderItem> findItems(long orderId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM order_items WHERE order_id=?",
        (rs, row) -> new OrderItem(rs.getLong("sku_id"), rs.getInt("quantity")),
        orderId);
  }

  public int markPaymentOrderActionRequired(long paymentId) {
    return jdbc.update(
        "UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=(SELECT order_id"
            + " FROM payments WHERE id=?)",
        paymentId);
  }

  public int recordReconciliation(int attempts, Timestamp now, long paymentId) {
    return jdbc.update(
        "UPDATE payments SET reconciliation_attempts=?,last_reconciled_at=? WHERE id=?",
        attempts,
        now,
        paymentId);
  }

  public ReconciliationRow lockReconciliation(long paymentId) {
    return jdbc
        .query(
            "SELECT reconciliation_attempts,status FROM payments WHERE id=? FOR UPDATE",
            (rs, row) ->
                new ReconciliationRow(rs.getInt("reconciliation_attempts"), rs.getString("status")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public int releaseStockHold(long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET status='SCHEDULED',hold_reason=NULL WHERE id=? AND"
            + " status='HELD' AND hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE'",
        scheduleId);
  }

  public Long lastInsertedId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  public int insertAttempt(
      long orderId,
      BigDecimal amount,
      String providerOrderId,
      String idempotencyKey,
      int attempt,
      Timestamp requestedAt,
      Timestamp createdAt) {
    return jdbc.update(
        "INSERT INTO"
            + " payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,created_at)"
            + " VALUES (?,'BILLING','TOSS','READY',?,?,?,?,?,?)",
        orderId,
        amount,
        providerOrderId,
        idempotencyKey,
        attempt,
        requestedAt,
        createdAt);
  }

  public int holdStockUnavailable(long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET"
            + " status='HELD',hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE' WHERE id=?",
        scheduleId);
  }

  public RetryOrder lockOrder(long orderId) {
    return jdbc
        .query(
            "SELECT payment_amount,status FROM orders WHERE id=? FOR UPDATE",
            (rs, row) -> new RetryOrder(rs.getBigDecimal("payment_amount"), rs.getString("status")),
            orderId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Long findExistingAttempt(long orderId, int attempt) {
    return jdbc.query(
        "SELECT id FROM payments WHERE order_id=? AND attempt_no=?",
        rs -> rs.next() ? rs.getLong(1) : null,
        orderId,
        attempt);
  }

  public Integer countStockHolds(long scheduleId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM subscription_schedules WHERE id=? AND status='HELD' AND"
            + " hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE'",
        Integer.class,
        scheduleId);
  }

  public RetryAttempt lockRetryAttempt(long paymentId) {
    return jdbc
        .query(
            """
            SELECT payment.order_id,payment.attempt_no,payment.status,context.schedule_id
            FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
            WHERE payment.id=? FOR UPDATE\
            """,
            (rs, row) ->
                new RetryAttempt(
                    rs.getLong("order_id"),
                    rs.getInt("attempt_no"),
                    rs.getString("status"),
                    rs.getLong("schedule_id")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public int holdRetryExhausted(long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET"
            + " status='HELD',hold_reason='PAYMENT_RETRY_EXHAUSTED' WHERE id=?",
        scheduleId);
  }

  public int markOrderActionRequired(long orderId) {
    return jdbc.update("UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=?", orderId);
  }

  public int markFailed(String providerStatus, String failureCode, Timestamp now, long paymentId) {
    return jdbc.update(
        "UPDATE payments SET status='FAILED',provider_status=?,failure_code=?,failed_at=?"
            + " WHERE id=?",
        providerStatus,
        failureCode,
        now,
        paymentId);
  }

  public FailureAttempt lockFailureAttempt(long paymentId) {
    return jdbc
        .query(
            """
            SELECT payment.id,payment.order_id,payment.attempt_no,payment.status,context.schedule_id
            FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
            WHERE payment.id=? FOR UPDATE\
            """,
            (rs, row) ->
                new FailureAttempt(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getInt("attempt_no"),
                    rs.getString("status"),
                    rs.getLong("schedule_id")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public record FailureAttempt(
      long id, long orderId, int attemptNo, String status, long scheduleId) {}

  public record RetryAttempt(long orderId, int attemptNo, String status, long scheduleId) {}

  public record RetryOrder(BigDecimal amount, String status) {}

  public record ReconciliationRow(int attempts, String status) {}

  public record OrderItem(long skuId, int quantity) {}

  public record InventoryState(int available, int reserved, long version) {}
}
