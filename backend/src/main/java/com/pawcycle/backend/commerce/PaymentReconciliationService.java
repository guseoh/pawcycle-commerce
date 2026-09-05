package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.payment.api.PaymentReconciliationResponse;
import com.pawcycle.backend.commerce.payment.persistence.PaymentReconciliationPersistenceAdapter;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentReconciliationService {
  private final PaymentReconciliationPersistenceAdapter payments;
  private final TransactionTemplate transaction;
  private final TossPaymentAdapter paymentProvider;
  private final TossBillingAdapter billingProvider;
  private final NotificationService notifications;
  private final MembershipEvaluationService membershipEvaluation;
  private final InventoryService inventory;
  private final AdminAuditService audits;
  private final SubscriptionBillingService billingFailures;
  private final DeliveryService deliveries;
  private final Clock clock;

  public PaymentReconciliationService(
      PaymentReconciliationPersistenceAdapter payments,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossPaymentAdapter paymentProvider,
      TossBillingAdapter billingProvider,
      NotificationService notifications,
      MembershipEvaluationService membershipEvaluation,
      InventoryService inventory,
      AdminAuditService audits,
      SubscriptionBillingService billingFailures,
      DeliveryService deliveries,
      Clock clock) {
    this.payments = payments;
    this.transaction = new TransactionTemplate(transactionManager);
    this.paymentProvider = paymentProvider;
    this.billingProvider = billingProvider;
    this.notifications = notifications;
    this.membershipEvaluation = membershipEvaluation;
    this.inventory = inventory;
    this.audits = audits;
    this.billingFailures = billingFailures;
    this.deliveries = deliveries;
    this.clock = clock;
  }

  public PaymentReconciliationResponse reconcile(long id) {
    return reconcile(id, null);
  }

  public PaymentReconciliationResponse reconcile(long id, Long adminId) {
    PaymentReconciliationPersistenceAdapter.ReconciliationWork work =
        transaction.execute(
            status -> {
              PaymentReconciliationPersistenceAdapter.ReconciliationWork row = payments.findForStart(id);
              if (row == null) throw notFound();
              boolean billingProcessing = "BILLING".equals(row.type()) && "PROCESSING".equals(row.status());
              if (!billingProcessing && !"UNKNOWN".equals(row.status())) {
                throw new CommerceException(409, "PAYMENT_RECONCILIATION_NOT_ALLOWED", "UNKNOWN 결제 또는 처리 중 Billing만 대사할 수 있습니다.");
              }
              boolean configured = "BILLING".equals(row.type()) ? billingProvider.isConfigured() : paymentProvider.isConfigured();
              if (!configured) throw new CommerceException(503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
              if (row.attempts() >= 10) throw new CommerceException(409, "PAYMENT_RECONCILIATION_EXHAUSTED", "결제 대사 횟수를 초과했습니다.");
              payments.incrementAttempts(id, row.attempts() + 1);
              return row;
            });

    ProviderResult observed;
    try {
      if ("BILLING".equals(work.type())) {
        TossBillingAdapter.ChargeResult result = billingProvider.queryCharge(work.providerOrderId());
        observed = new ProviderResult(result.status(), result.providerStatus());
      } else {
        TossPaymentAdapter.ConfirmResult result = paymentProvider.queryPayment(work.providerOrderId());
        observed = new ProviderResult(result.status(), result.providerStatus());
      }
    } catch (RuntimeException exception) {
      observed = new ProviderResult("UNKNOWN", "NO_RESPONSE");
    }

    ProviderResult observation = observed;
    boolean billingFailure = transaction.execute(status -> complete(id, adminId, observation));
    if (billingFailure) {
      billingFailures.recordExplicitFailure(id, "RECONCILED_FAILED", observation.providerStatus());
      billingFailures.prepareNextAttempt(id);
    }
    return response(payments.find(id));
  }

  private boolean complete(long paymentId, Long adminId, ProviderResult observed) {
    PaymentReconciliationPersistenceAdapter.ReconciliationTarget payment = payments.findForCompletion(paymentId);
    if (payment == null) throw notFound();
    boolean billingProcessing = "BILLING".equals(payment.type()) && "PROCESSING".equals(payment.status());
    if (!billingProcessing && !"UNKNOWN".equals(payment.status())) return false;
    if ("SUCCEEDED".equals(observed.status())) {
      Timestamp paidAt = Timestamp.from(clock.instant());
      for (PaymentReconciliationPersistenceAdapter.OrderItem item : payments.findOrderItems(payment.orderId())) {
        inventory.deduct(item.skuId(), item.quantity(), paymentId);
      }
      payments.markSucceeded(paymentId, observed.providerStatus(), paidAt);
      payments.markOrderPaid(payment.orderId(), paidAt);
      deliveries.createPreparing(payment.orderId());
      payments.useReservedCoupon(payment.orderId(), paidAt);
      if ("ONE_TIME".equals(payment.source())) payments.consumeCart(payment.memberId(), payment.orderId());
      membershipEvaluation.evaluate(payment.memberId());
      notifications.create(payment.memberId(), "ORDER_PAID", "ORDER", payment.orderId());
    } else if ("FAILED".equals(observed.status())) {
      if ("BILLING".equals(payment.type())) {
        if (adminId != null) audits.append(adminId, "PAYMENT_RECONCILE", "PAYMENT", paymentId);
        return true;
      }
      for (PaymentReconciliationPersistenceAdapter.OrderItem item : payments.findOrderItems(payment.orderId())) {
        inventory.release(item.skuId(), item.quantity(), paymentId);
      }
      payments.markFailed(paymentId, observed.providerStatus());
      payments.markOrderPaymentFailed(payment.orderId());
      payments.releaseReservedCoupon(payment.orderId());
    } else if (payment.attempts() >= 10) {
      payments.markOrderActionRequired(payment.orderId());
      notifications.create(payment.memberId(), "PAYMENT_ACTION_REQUIRED", "PAYMENT", paymentId);
    }
    if (adminId != null) audits.append(adminId, "PAYMENT_RECONCILE", "PAYMENT", paymentId);
    return false;
  }

  private static PaymentReconciliationResponse response(PaymentReconciliationPersistenceAdapter.PaymentReconciliationView view) {
    if (view == null) throw notFound();
    return new PaymentReconciliationResponse(view.paymentId(), view.orderId(), view.status(), view.attempts(), view.lastReconciledAt());
  }

  private static CommerceException notFound() {
    return new CommerceException(404, "PAYMENT_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }

  private record ProviderResult(String status, String providerStatus) {}

}
