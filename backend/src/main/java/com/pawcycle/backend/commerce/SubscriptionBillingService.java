package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional state transitions for billing attempts. Scheduling and provider execution are
 * intentionally not enabled in this repository-preparation task.
 */
@Service
public class SubscriptionBillingService {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate transaction;
  private final AdminAuditService audits;
  private final InventoryService inventory;
  private final Clock clock;

  public SubscriptionBillingService(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      AdminAuditService audits,
      InventoryService inventory,
      Clock clock) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(manager);
    this.audits = audits;
    this.inventory = inventory;
    this.clock = clock;
  }

  public void recordExplicitFailure(long paymentId, String failureCode) {
    recordExplicitFailure(paymentId, failureCode, "ABORTED");
  }

  public void recordExplicitFailure(long paymentId, String failureCode, String providerStatus) {
    transaction.executeWithoutResult(
        status -> {
          Map<String, Object> payment =
              jdbc.query(
                  """
                  SELECT payment.id,payment.order_id,payment.attempt_no,payment.status,context.schedule_id
                  FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
                  WHERE payment.id=? FOR UPDATE\
                  """,
                  rs ->
                      rs.next()
                          ? Map.of(
                              "id",
                              rs.getLong("id"),
                              "orderId",
                              rs.getLong("order_id"),
                              "attemptNo",
                              rs.getInt("attempt_no"),
                              "status",
                              rs.getString("status"),
                              "scheduleId",
                              rs.getLong("schedule_id"))
                          : null,
                  paymentId);
          if (payment == null)
            throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
          if (!"PROCESSING".equals(payment.get("status")))
            throw new CommerceException(
                409, "PAYMENT_STATE_CONFLICT", "처리 중인 Billing 결제만 실패 처리할 수 있습니다.");
          jdbc.update(
              "UPDATE payments SET status='FAILED',provider_status=?,failure_code=?,failed_at=?"
                  + " WHERE id=?",
              providerStatus,
              failureCode,
              now(),
              paymentId);
          releaseReservation((Long) payment.get("orderId"), paymentId);
          if ((Integer) payment.get("attemptNo") >= 3) {
            jdbc.update(
                "UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=?",
                payment.get("orderId"));
            jdbc.update(
                "UPDATE subscription_schedules SET"
                    + " status='HELD',hold_reason='PAYMENT_RETRY_EXHAUSTED' WHERE id=?",
                payment.get("scheduleId"));
          }
        });
  }

  /** Creates the next attempt for the same order only after an explicit failed attempt. */
  public long prepareNextAttempt(long failedPaymentId) {
    return prepareNextAttempt(failedPaymentId, null, false);
  }

  public long retryHeldBilling(long failedPaymentId, long adminId) {
    return prepareNextAttempt(failedPaymentId, adminId, true);
  }

  private long prepareNextAttempt(long failedPaymentId, Long adminId, boolean explicit) {
    return transaction.execute(
        status -> {
          Map<String, Object> payment =
              jdbc.query(
                  """
                  SELECT payment.order_id,payment.attempt_no,payment.status,context.schedule_id
                  FROM payments payment JOIN subscription_order_context context ON context.order_id=payment.order_id
                  WHERE payment.id=? FOR UPDATE\
                  """,
                  rs ->
                      rs.next()
                          ? Map.of(
                              "orderId",
                              rs.getLong(1),
                              "attemptNo",
                              rs.getInt(2),
                              "status",
                              rs.getString(3),
                              "scheduleId",
                              rs.getLong(4))
                          : null,
                  failedPaymentId);
          if (payment == null)
            throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
          int nextAttempt = (Integer) payment.get("attemptNo") + 1;
          if (!"FAILED".equals(payment.get("status")) || nextAttempt > 3)
            throw new CommerceException(409, "PAYMENT_RETRY_NOT_ALLOWED", "다음 결제 시도를 만들 수 없습니다.");
          if (explicit
              && jdbc.queryForObject(
                      "SELECT COUNT(*) FROM subscription_schedules WHERE id=? AND status='HELD' AND"
                          + " hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE'",
                      Integer.class,
                      payment.get("scheduleId"))
                  != 1) {
            throw new CommerceException(
                409, "PAYMENT_RETRY_NOT_ALLOWED", "재고 부족으로 보류된 Billing만 명시적으로 재시도할 수 있습니다.");
          }
          Long existingAttempt =
              jdbc.query(
                  "SELECT id FROM payments WHERE order_id=? AND attempt_no=?",
                  rs -> rs.next() ? rs.getLong(1) : null,
                  payment.get("orderId"),
                  nextAttempt);
          if (existingAttempt != null) {
            if (adminId != null)
              audits.append(adminId, "BILLING_RETRY_DUPLICATE", "PAYMENT", failedPaymentId);
            return existingAttempt;
          }
          Map<String, Object> order =
              jdbc.query(
                  "SELECT payment_amount,status FROM orders WHERE id=? FOR UPDATE",
                  rs ->
                      rs.next()
                          ? Map.of("amount", rs.getBigDecimal(1), "status", rs.getString(2))
                          : null,
                  payment.get("orderId"));
          if (order == null || "PAYMENT_ACTION_REQUIRED".equals(order.get("status")))
            throw new CommerceException(409, "PAYMENT_RETRY_NOT_ALLOWED", "주문 결제를 재시도할 수 없습니다.");
          java.util.List<Reservation> reservations;
          try {
            reservations = lockReservations((Long) payment.get("orderId"));
          } catch (CommerceException exception) {
            if (!"INVENTORY_INSUFFICIENT".equals(exception.code())) throw exception;
            jdbc.update(
                "UPDATE subscription_schedules SET"
                    + " status='HELD',hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE' WHERE id=?",
                payment.get("scheduleId"));
            if (adminId != null)
              audits.append(adminId, "BILLING_RETRY_STOCK_UNAVAILABLE", "PAYMENT", failedPaymentId);
            return 0L;
          }
          jdbc.update(
              "INSERT INTO"
                  + " payments(order_id,type,provider,status,amount,provider_order_id,idempotency_key,attempt_no,requested_at,created_at)"
                  + " VALUES (?,'BILLING','TOSS','READY',?,?,?,?,?,?)",
              payment.get("orderId"),
              order.get("amount"),
              "TOSS-SUB-" + UUID.randomUUID(),
              "billing-" + UUID.randomUUID(),
              nextAttempt,
              now(),
              now());
          long nextId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
          applyReservations(reservations, nextId);
          jdbc.update(
              "UPDATE subscription_schedules SET status='SCHEDULED',hold_reason=NULL WHERE id=? AND"
                  + " status='HELD' AND hold_reason='PAYMENT_RETRY_STOCK_UNAVAILABLE'",
              payment.get("scheduleId"));
          if (adminId != null) audits.append(adminId, "BILLING_RETRY", "PAYMENT", nextId);
          return nextId;
        });
  }

  /**
   * Records one reconciliation observation; callers must not turn an UNKNOWN result into a retry.
   */
  public boolean recordUnknownReconciliationAttempt(long paymentId) {
    return Boolean.TRUE.equals(
        transaction.execute(
            status -> {
              Map<String, Object> payment =
                  jdbc.query(
                      "SELECT reconciliation_attempts,status FROM payments WHERE id=? FOR UPDATE",
                      rs ->
                          rs.next()
                              ? Map.of("attempts", rs.getInt(1), "status", rs.getString(2))
                              : null,
                      paymentId);
              if (payment == null || !"UNKNOWN".equals(payment.get("status"))) return false;
              int currentAttempts = (Integer) payment.get("attempts");
              if (currentAttempts >= 10) return false;
              int attempts = currentAttempts + 1;
              jdbc.update(
                  "UPDATE payments SET reconciliation_attempts=?,last_reconciled_at=? WHERE id=?",
                  attempts,
                  now(),
                  paymentId);
              if (attempts >= 10)
                jdbc.update(
                    "UPDATE orders SET status='PAYMENT_ACTION_REQUIRED' WHERE id=(SELECT order_id"
                        + " FROM payments WHERE id=?)",
                    paymentId);
              return attempts < 10;
            }));
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private void releaseReservation(long orderId, long paymentId) {
    for (Map<String, Object> item :
        jdbc.queryForList("SELECT sku_id,quantity FROM order_items WHERE order_id=?", orderId)) {
      long skuId = ((Number) item.get("sku_id")).longValue();
      int quantity = ((Number) item.get("quantity")).intValue();
      inventory.release(skuId, quantity, paymentId);
    }
  }

  private java.util.List<Reservation> lockReservations(long orderId) {
    Map<Long, Map<String, Object>> inventories = new java.util.LinkedHashMap<>();
    Map<Long, Integer> quantities = new java.util.LinkedHashMap<>();
    for (Map<String, Object> item :
        jdbc.queryForList(
            "SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id", orderId)) {
      long skuId = ((Number) item.get("sku_id")).longValue();
      int quantity = ((Number) item.get("quantity")).intValue();
      quantities.merge(skuId, quantity, Integer::sum);
    }
    for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
      Map<String, Object> inventory =
          jdbc.query(
              "SELECT available_quantity,reserved_quantity,version FROM inventories WHERE sku_id=?"
                  + " FOR UPDATE",
              rs ->
                  rs.next()
                      ? Map.of(
                          "available",
                          rs.getInt(1),
                          "reserved",
                          rs.getInt(2),
                          "version",
                          rs.getLong(3))
                      : null,
              entry.getKey());
      if (inventory == null || ((Number) inventory.get("available")).intValue() < entry.getValue())
        throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
      inventories.put(entry.getKey(), inventory);
    }
    return quantities.entrySet().stream()
        .map(
            entry -> {
              Map<String, Object> inventory = inventories.get(entry.getKey());
              return new Reservation(
                  entry.getKey(),
                  entry.getValue(),
                  ((Number) inventory.get("available")).intValue(),
                  ((Number) inventory.get("reserved")).intValue(),
                  ((Number) inventory.get("version")).longValue());
            })
        .toList();
  }

  private void applyReservations(java.util.List<Reservation> reservations, long paymentId) {
    for (Reservation reservation : reservations) {
      inventory.reserve(reservation.skuId(), reservation.quantity(), paymentId);
    }
  }

  private record Reservation(
      long skuId, int quantity, int availableBefore, int reservedBefore, long version) {}
}
