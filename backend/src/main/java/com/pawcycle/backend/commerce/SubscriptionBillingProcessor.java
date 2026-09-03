package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.pawcycle.backend.foundation.persistence.NativeQueryExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes at most the READY attempt selected at the start of a processor cycle. */
@Service
public class SubscriptionBillingProcessor {
  private final NativeQueryExecutor jdbc;
  private final TransactionTemplate tx;
  private final TossBillingAdapter provider;
  private final SubscriptionBillingService retries;
  private final PaymentReconciliationService reconciliation;
  private final DeliveryService deliveries;
  private final NotificationService notifications;
  private final MembershipEvaluationService membershipEvaluation;
  private final InventoryService inventory;
  private final Clock clock;

  public SubscriptionBillingProcessor(
      NativeQueryExecutor jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      TossBillingAdapter provider,
      SubscriptionBillingService retries,
      PaymentReconciliationService reconciliation,
      DeliveryService deliveries,
      NotificationService notifications,
      MembershipEvaluationService membershipEvaluation,
      InventoryService inventory,
      Clock clock) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.provider = provider;
    this.retries = retries;
    this.reconciliation = reconciliation;
    this.deliveries = deliveries;
    this.notifications = notifications;
    this.membershipEvaluation = membershipEvaluation;
    this.inventory = inventory;
    this.clock = clock;
  }

  public int processReadyPayments() {
    if (!provider.isConfigured())
      throw new CommerceException(
          503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
    List<Map<String, Object>> candidates =
        jdbc.query(
            "SELECT id,status FROM payments WHERE type='BILLING' AND status IN"
                + " ('READY','PROCESSING') ORDER BY id",
            (rs, row) -> Map.of("id", rs.getLong(1), "status", rs.getString(2)));
    for (Map<String, Object> candidate : candidates) {
      long id = ((Number) candidate.get("id")).longValue();
      try {
        if ("PROCESSING".equals(candidate.get("status"))) reconciliation.reconcile(id);
        else process(id);
      } catch (CommerceException ignored) {
        /* isolate one payment so later READY attempts can continue */
      }
    }
    return candidates.size();
  }

  public void process(long paymentId) {
    if (!provider.isConfigured())
      throw new CommerceException(
          503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss Billing Provider가 현재 환경에 구성되지 않았습니다.");
    Map<String, Object> work =
        tx.execute(
            status -> {
              Map<String, Object> row =
                  one(
                      """
                      SELECT payment.id,payment.order_id,payment.provider_order_id,payment.amount,payment.status,
                             method.billing_key,context.schedule_id,context.subscription_id,orders.member_id
                      FROM payments payment
                      JOIN orders orders ON orders.id=payment.order_id
                      JOIN subscription_order_context context ON context.order_id=payment.order_id
                      LEFT JOIN billing_payment_methods method ON method.member_id=orders.member_id AND method.status='ACTIVE'
                      WHERE payment.id=? FOR UPDATE\
                      """,
                      paymentId);
              if (row == null)
                throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
              if (!"READY".equals(row.get("status"))) return null;
              if (row.get("billing_key") == null) {
                jdbc.update(
                    "UPDATE subscription_schedules SET"
                        + " status='HELD',hold_reason='MISSING_BILLING_METHOD' WHERE id=?",
                    row.get("schedule_id"));
                notifications.create(
                    ((Number) row.get("member_id")).longValue(),
                    "SUBSCRIPTION_HELD",
                    "SUBSCRIPTION",
                    ((Number) row.get("subscription_id")).longValue());
                return null;
              }
              jdbc.update("UPDATE payments SET status='PROCESSING' WHERE id=?", paymentId);
              return row;
            });
    if (work == null) return;

    TossBillingAdapter.ChargeResult result;
    try {
      result =
          provider.charge(
              (String) work.get("billing_key"),
              (String) work.get("provider_order_id"),
              (java.math.BigDecimal) work.get("amount"));
    } catch (RuntimeException exception) {
      result = new TossBillingAdapter.ChargeResult("UNKNOWN", "NO_RESPONSE");
    }
    if ("SUCCEEDED".equals(result.status())) completeSuccess(paymentId, result.providerStatus());
    else if ("FAILED".equals(result.status())) {
      retries.recordExplicitFailure(paymentId, "TOSS_REJECTED");
      retries.prepareNextAttempt(paymentId);
    } else markUnknown(paymentId, result.providerStatus());
  }

  void completeSuccess(long paymentId, String providerStatus) {
    tx.executeWithoutResult(
        status -> {
          Map<String, Object> payment =
              one(
                  """
                  SELECT payment.id,payment.order_id,orders.member_id,context.schedule_id
                  FROM payments payment
                  JOIN orders orders ON orders.id=payment.order_id
                  JOIN subscription_order_context context ON context.order_id=payment.order_id
                  WHERE payment.id=? AND payment.status='PROCESSING' FOR UPDATE\
                  """,
                  paymentId);
          if (payment == null) return;
          long orderId = ((Number) payment.get("order_id")).longValue();
          for (Map<String, Object> item :
              jdbc.queryForList(
                  "SELECT sku_id,quantity FROM order_items WHERE order_id=? ORDER BY sku_id",
                  orderId))
            inventory.deduct(
                ((Number) item.get("sku_id")).longValue(),
                ((Number) item.get("quantity")).intValue(),
                paymentId);
          Timestamp now = Timestamp.from(clock.instant());
          jdbc.update(
              "UPDATE payments SET status='SUCCEEDED',provider_status=?,approved_at=? WHERE id=?",
              providerStatus,
              now,
              paymentId);
          jdbc.update("UPDATE orders SET status='PAID',paid_at=? WHERE id=?", now, orderId);
          jdbc.update(
              "UPDATE subscription_schedules SET status='SCHEDULED',hold_reason=NULL WHERE id=? AND"
                  + " status='HELD' AND hold_reason='MISSING_BILLING_METHOD'",
              payment.get("schedule_id"));
          deliveries.createPreparing(orderId);
          membershipEvaluation.evaluate(((Number) payment.get("member_id")).longValue());
          notifications.create(
              ((Number) payment.get("member_id")).longValue(), "ORDER_PAID", "ORDER", orderId);
        });
  }

  private void markUnknown(long paymentId, String providerStatus) {
    tx.executeWithoutResult(
        status ->
            jdbc.update(
                "UPDATE payments SET"
                    + " status='UNKNOWN',provider_status=?,failure_code='PROVIDER_RESULT_UNKNOWN'"
                    + " WHERE id=? AND status='PROCESSING'",
                providerStatus,
                paymentId));
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : new LinkedHashMap<>(rows.getFirst());
  }
}
