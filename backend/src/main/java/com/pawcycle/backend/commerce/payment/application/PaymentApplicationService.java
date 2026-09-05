package com.pawcycle.backend.commerce.payment.application;

import com.pawcycle.backend.commerce.CommerceException;
import com.pawcycle.backend.commerce.DeliveryService;
import com.pawcycle.backend.commerce.InventoryService;
import com.pawcycle.backend.commerce.MembershipEvaluationService;
import com.pawcycle.backend.commerce.NotificationService;
import com.pawcycle.backend.commerce.TossPaymentAdapter;
import com.pawcycle.backend.commerce.payment.api.PaymentResponse;
import com.pawcycle.backend.commerce.payment.persistence.PaymentPersistenceAdapter;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentApplicationService {
  private final PaymentPersistenceAdapter payments;
  private final TransactionTemplate transaction;
  private final TossPaymentAdapter tossPaymentAdapter;
  private final DeliveryService deliveryService;
  private final NotificationService notificationService;
  private final InventoryService inventoryService;
  private final MembershipEvaluationService membershipEvaluation;

  public PaymentApplicationService(
      PaymentPersistenceAdapter payments,
      org.springframework.transaction.PlatformTransactionManager transactionManager,
      TossPaymentAdapter tossPaymentAdapter,
      DeliveryService deliveryService,
      NotificationService notificationService,
      InventoryService inventoryService,
      MembershipEvaluationService membershipEvaluation) {
    this.payments = payments;
    this.transaction = new TransactionTemplate(transactionManager);
    this.tossPaymentAdapter = tossPaymentAdapter;
    this.deliveryService = deliveryService;
    this.notificationService = notificationService;
    this.inventoryService = inventoryService;
    this.membershipEvaluation = membershipEvaluation;
  }

  public PaymentResponse confirm(
      long memberId, String paymentKey, String providerOrderId, BigDecimal amount) {
    if (!tossPaymentAdapter.isConfigured()) {
      throw new CommerceException(503, "PAYMENT_PROVIDER_UNAVAILABLE", "Toss 결제 Provider가 현재 환경에 구성되지 않았습니다.");
    }
    PaymentPersistenceAdapter.PaymentWork work =
        transaction.execute(
            status -> {
              PaymentPersistenceAdapter.PaymentWork payment =
                  payments.findByProviderOrderIdForUpdate(providerOrderId);
              if (payment == null) throw notFound();
              if (payment.memberId() != memberId) {
                throw new CommerceException(403, "PAYMENT_FORBIDDEN", "결제 소유자가 아닙니다.");
              }
              if ("SUCCEEDED".equals(payment.status())
                  && "PAID".equals(payment.orderStatus())
                  && payment.amount().compareTo(amount) == 0
                  && paymentKey.equals(payment.paymentKey())) {
                return payment;
              }
              if (!"READY".equals(payment.status())
                  || !"PAYMENT_PENDING".equals(payment.orderStatus())
                  || payment.amount().compareTo(amount) != 0) {
                throw new CommerceException(409, "PAYMENT_CONFIRM_CONFLICT", "결제 확인 상태가 올바르지 않습니다.");
              }
              payments.markProcessing(payment.paymentId(), paymentKey);
              return payment;
            });

    if ("SUCCEEDED".equals(work.status()) && "PAID".equals(work.orderStatus())) {
      return new PaymentResponse(work.paymentId(), work.orderId(), "SUCCEEDED");
    }
    TossPaymentAdapter.ConfirmResult result;
    try {
      result = tossPaymentAdapter.confirm(paymentKey, providerOrderId, amount);
    } catch (RuntimeException exception) {
      return transaction.execute(status -> markProviderUnknown(work.paymentId()));
    }
    return transaction.execute(status -> finalizePayment(work.paymentId(), result.status(), paymentKey));
  }

  private PaymentResponse finalizePayment(long paymentId, String providerResult, String paymentKey) {
    PaymentPersistenceAdapter.PaymentState payment = payments.findForUpdate(paymentId);
    if (payment == null) throw notFound();
    if (!"PROCESSING".equals(payment.status())) {
      return new PaymentResponse(payment.paymentId(), payment.orderId(), payment.status());
    }
    if ("UNKNOWN".equals(providerResult)) {
      payments.markUnknown(paymentId);
      return new PaymentResponse(paymentId, payment.orderId(), "UNKNOWN");
    }
    if ("SUCCEEDED".equals(providerResult)) {
      for (PaymentPersistenceAdapter.OrderItem item : payments.findOrderItems(payment.orderId())) {
        inventoryService.deduct(item.skuId(), item.quantity(), paymentId);
      }
      payments.markSucceeded(paymentId, paymentKey);
      payments.markOrderPaid(payment.orderId());
      deliveryService.createPreparing(payment.orderId());
      payments.useReservedCoupon(payment.orderId());
      notificationService.create(
          payments.findMemberId(payment.orderId()), "ORDER_PAID", "ORDER", payment.orderId());
      payments.consumeCart(payments.findMemberId(payment.orderId()), payment.orderId());
      membershipEvaluation.evaluate(payments.findMemberId(payment.orderId()));
      return new PaymentResponse(paymentId, payment.orderId(), "SUCCEEDED");
    }
    for (PaymentPersistenceAdapter.OrderItem item : payments.findOrderItems(payment.orderId())) {
      inventoryService.release(item.skuId(), item.quantity(), paymentId);
    }
    payments.markFailed(paymentId);
    payments.markOrderPaymentFailed(payment.orderId());
    payments.releaseReservedCoupon(payment.orderId());
    return new PaymentResponse(paymentId, payment.orderId(), "FAILED");
  }

  private PaymentResponse markProviderUnknown(long paymentId) {
    PaymentPersistenceAdapter.PaymentState payment = payments.findForUpdate(paymentId);
    if (payment == null) throw notFound();
    if ("PROCESSING".equals(payment.status())) payments.markUnknown(paymentId);
    return new PaymentResponse(paymentId, payment.orderId(), "UNKNOWN");
  }

  private static CommerceException notFound() {
    return new CommerceException(404, "PAYMENT_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
  }
}
