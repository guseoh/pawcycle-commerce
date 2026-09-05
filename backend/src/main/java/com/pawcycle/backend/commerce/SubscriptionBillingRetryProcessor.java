package com.pawcycle.backend.commerce;

import com.pawcycle.backend.subscription.persistence.SubscriptionBillingRetryPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionBillingRetryPersistence.InventoryState;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional state transitions for billing attempts. Scheduling and provider execution are
 * intentionally not enabled in this repository-preparation task.
 */
@org.springframework.stereotype.Component
public class SubscriptionBillingRetryProcessor {
  private final SubscriptionBillingRetryPersistence persistence;
  private final TransactionTemplate transaction;
  private final AdminAuditService audits;
  private final InventoryService inventory;
  private final Clock clock;

  public SubscriptionBillingRetryProcessor(
      SubscriptionBillingRetryPersistence persistence,
      org.springframework.transaction.PlatformTransactionManager manager,
      AdminAuditService audits,
      InventoryService inventory,
      Clock clock) {
    this.persistence = persistence;
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
          var payment = persistence.lockFailureAttempt(paymentId);
          if (payment == null)
            throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
          if (!"PROCESSING".equals(payment.status()))
            throw new CommerceException(
                409, "PAYMENT_STATE_CONFLICT", "처리 중인 Billing 결제만 실패 처리할 수 있습니다.");
          persistence.markFailed(providerStatus, failureCode, now(), paymentId);
          releaseReservation(payment.orderId(), paymentId);
          if (payment.attemptNo() >= 3) {
            persistence.markOrderActionRequired(payment.orderId());
            persistence.holdRetryExhausted(payment.scheduleId());
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
          var payment = persistence.lockRetryAttempt(failedPaymentId);
          if (payment == null)
            throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
          int nextAttempt = payment.attemptNo() + 1;
          if (!"FAILED".equals(payment.status()) || nextAttempt > 3)
            throw new CommerceException(409, "PAYMENT_RETRY_NOT_ALLOWED", "다음 결제 시도를 만들 수 없습니다.");
          if (explicit && persistence.countStockHolds(payment.scheduleId()) != 1) {
            throw new CommerceException(
                409, "PAYMENT_RETRY_NOT_ALLOWED", "재고 부족으로 보류된 Billing만 명시적으로 재시도할 수 있습니다.");
          }
          Long existingAttempt = persistence.findExistingAttempt(payment.orderId(), nextAttempt);
          if (existingAttempt != null) {
            if (adminId != null)
              audits.append(adminId, "BILLING_RETRY_DUPLICATE", "PAYMENT", failedPaymentId);
            return existingAttempt;
          }
          var order = persistence.lockOrder(payment.orderId());
          if (order == null || "PAYMENT_ACTION_REQUIRED".equals(order.status()))
            throw new CommerceException(409, "PAYMENT_RETRY_NOT_ALLOWED", "주문 결제를 재시도할 수 없습니다.");
          java.util.List<Reservation> reservations;
          try {
            reservations = lockReservations(payment.orderId());
          } catch (CommerceException exception) {
            if (!"INVENTORY_INSUFFICIENT".equals(exception.code())) throw exception;
            persistence.holdStockUnavailable(payment.scheduleId());
            if (adminId != null)
              audits.append(adminId, "BILLING_RETRY_STOCK_UNAVAILABLE", "PAYMENT", failedPaymentId);
            return 0L;
          }
          persistence.insertAttempt(
              payment.orderId(),
              order.amount(),
              "TOSS-SUB-" + UUID.randomUUID(),
              "billing-" + UUID.randomUUID(),
              nextAttempt,
              now(),
              now());
          long nextId = persistence.lastInsertedId();
          applyReservations(reservations, nextId);
          persistence.releaseStockHold(payment.scheduleId());
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
              var payment = persistence.lockReconciliation(paymentId);
              if (payment == null || !"UNKNOWN".equals(payment.status())) return false;
              int currentAttempts = payment.attempts();
              if (currentAttempts >= 10) return false;
              int attempts = currentAttempts + 1;
              persistence.recordReconciliation(attempts, now(), paymentId);
              if (attempts >= 10) persistence.markPaymentOrderActionRequired(paymentId);
              return attempts < 10;
            }));
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  private void releaseReservation(long orderId, long paymentId) {
    for (var item : persistence.findItems(orderId)) {
      long skuId = item.skuId();
      int quantity = item.quantity();
      inventory.release(skuId, quantity, paymentId);
    }
  }

  private java.util.List<Reservation> lockReservations(long orderId) {
    Map<Long, InventoryState> inventories = new java.util.LinkedHashMap<>();
    Map<Long, Integer> quantities = new java.util.LinkedHashMap<>();
    for (var item : persistence.findOrderedItems(orderId)) {
      long skuId = item.skuId();
      int quantity = item.quantity();
      quantities.merge(skuId, quantity, Integer::sum);
    }
    for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
      var inventory = persistence.lockInventory(entry.getKey());
      if (inventory == null || inventory.available() < entry.getValue())
        throw new CommerceException(409, "INVENTORY_INSUFFICIENT", "재고가 부족합니다.");
      inventories.put(entry.getKey(), inventory);
    }
    return quantities.entrySet().stream()
        .map(
            entry -> {
              var inventory = inventories.get(entry.getKey());
              return new Reservation(
                  entry.getKey(),
                  entry.getValue(),
                  inventory.available(),
                  inventory.reserved(),
                  inventory.version());
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
