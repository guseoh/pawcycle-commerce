package com.pawcycle.backend.subscription.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionBillingPersistence {
  private final JdbcTemplate jdbc;

  public SubscriptionBillingPersistence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int markUnknown(String providerStatus, long paymentId) {
    return jdbc.update(
        "UPDATE payments SET"
            + " status='UNKNOWN',provider_status=?,failure_code='PROVIDER_RESULT_UNKNOWN'"
            + " WHERE id=? AND status='PROCESSING'",
        providerStatus,
        paymentId);
  }

  public int releaseMissingMethodHold(long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET status='SCHEDULED',hold_reason=NULL WHERE id=? AND"
            + " status='HELD' AND hold_reason='MISSING_BILLING_METHOD'",
        scheduleId);
  }

  public int markOrderPaid(Timestamp now, long orderId) {
    return jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", now, orderId);
  }

  public int markSucceeded(String providerStatus, Timestamp now, long paymentId) {
    return jdbc.update(
        "UPDATE payments SET status='SUCCEEDED',provider_status=?,approved_at=? WHERE id=?",
        providerStatus,
        now,
        paymentId);
  }

  public List<OrderItem> findOrderedItems(long orderId) {
    return jdbc.query(
        "SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id",
        (rs, row) -> new OrderItem(rs.getLong("sku_id"), rs.getInt("quantity")),
        orderId);
  }

  public ProcessingPayment lockProcessingPayment(long paymentId) {
    return jdbc
        .query(
            """
            SELECT payment.id,payment.order_id,orders.member_id,context.schedule_id
            FROM payments payment
            JOIN orders orders ON orders.id=payment.order_id
            JOIN subscription_order_context context ON context.order_id=payment.order_id
            WHERE payment.id=? AND payment.status='PROCESSING' FOR UPDATE\
            """,
            (rs, row) ->
                new ProcessingPayment(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getLong("member_id"),
                    rs.getLong("schedule_id")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public int markProcessing(long paymentId) {
    return jdbc.update("UPDATE payments SET status='PROCESSING' WHERE id=?", paymentId);
  }

  public int holdMissingMethod(long scheduleId) {
    return jdbc.update(
        "UPDATE subscription_schedules SET"
            + " status='HELD',hold_reason='MISSING_BILLING_METHOD' WHERE id=?",
        scheduleId);
  }

  public BillingWork lockWork(long paymentId) {
    return jdbc
        .query(
            """
            SELECT payment.id,payment.order_id,payment.provider_order_id,payment.amount,payment.status,
                   method.billing_key,context.schedule_id,context.subscription_id,orders.member_id
            FROM payments payment
            JOIN orders orders ON orders.id=payment.order_id
            JOIN subscription_order_context context ON context.order_id=payment.order_id
            LEFT JOIN billing_payment_methods method ON method.member_id=orders.member_id AND method.status='ACTIVE'
            WHERE payment.id=? FOR UPDATE\
            """,
            (rs, row) ->
                new BillingWork(
                    rs.getLong("id"),
                    rs.getLong("order_id"),
                    rs.getString("provider_order_id"),
                    rs.getBigDecimal("amount"),
                    rs.getString("status"),
                    rs.getString("billing_key"),
                    rs.getLong("schedule_id"),
                    rs.getLong("subscription_id"),
                    rs.getLong("member_id")),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public List<BillingCandidate> findCandidates() {
    return jdbc.query(
        "SELECT id,status FROM payments WHERE type='BILLING' AND status IN"
            + " ('READY','PROCESSING') ORDER BY id",
        (rs, row) -> new BillingCandidate(rs.getLong("id"), rs.getString("status")));
  }

  public record BillingCandidate(long id, String status) {}

  public record BillingWork(
      long id,
      long orderId,
      String providerOrderId,
      BigDecimal amount,
      String status,
      String billingKey,
      long scheduleId,
      long subscriptionId,
      long memberId) {}

  public record ProcessingPayment(long id, long orderId, long memberId, long scheduleId) {}

  public record OrderItem(long skuId, int quantity) {}
}
