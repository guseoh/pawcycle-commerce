package com.pawcycle.backend.commerce;

import com.pawcycle.backend.subscription.persistence.SubscriptionBillingPersistence;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes at most the READY attempt selected at the start of a processor cycle. */
@Service
public class SubscriptionBillingProcessor {
  private final SubscriptionBillingPersistence persistence;
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
      SubscriptionBillingPersistence persistence,
      org.springframework.transaction.PlatformTransactionManager manager,
      TossBillingAdapter provider,
      SubscriptionBillingService retries,
      PaymentReconciliationService reconciliation,
      DeliveryService deliveries,
      NotificationService notifications,
      MembershipEvaluationService membershipEvaluation,
      InventoryService inventory,
      Clock clock) {
    this.persistence = persistence;
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
    var candidates = persistence.findCandidates();
    for (var candidate : candidates) {
      long id = candidate.id();
      try {
        if ("PROCESSING".equals(candidate.status())) reconciliation.reconcile(id);
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
    var work =
        tx.execute(
            status -> {
              var row = persistence.lockWork(paymentId);
              if (row == null)
                throw new CommerceException(404, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다.");
              if (!"READY".equals(row.status())) return null;
              if (row.billingKey() == null) {
                persistence.holdMissingMethod(row.scheduleId());
                notifications.create(
                    row.memberId(), "SUBSCRIPTION_HELD", "SUBSCRIPTION", row.subscriptionId());
                return null;
              }
              persistence.markProcessing(paymentId);
              return row;
            });
    if (work == null) return;

    TossBillingAdapter.ChargeResult result;
    try {
      result = provider.charge(work.billingKey(), work.providerOrderId(), work.amount());
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
          var payment = persistence.lockProcessingPayment(paymentId);
          if (payment == null) return;
          long orderId = payment.orderId();
          for (var item : persistence.findOrderedItems(orderId))
            inventory.deduct(item.skuId(), item.quantity(), paymentId);
          Timestamp now = Timestamp.from(clock.instant());
          persistence.markSucceeded(providerStatus, now, paymentId);
          persistence.markOrderPaid(now, orderId);
          persistence.releaseMissingMethodHold(payment.scheduleId());
          deliveries.createPreparing(orderId);
          membershipEvaluation.evaluate(payment.memberId());
          notifications.create(payment.memberId(), "ORDER_PAID", "ORDER", orderId);
        });
  }

  private void markUnknown(long paymentId, String providerStatus) {
    tx.executeWithoutResult(status -> persistence.markUnknown(providerStatus, paymentId));
  }
}
