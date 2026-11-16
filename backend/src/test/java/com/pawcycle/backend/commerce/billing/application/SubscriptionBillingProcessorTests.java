package com.pawcycle.backend.commerce.billing.application;

import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.junit.jupiter.api.extension.ExtendWith;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.pawcycle.backend.common.external.ExternalCallFailures;
import com.pawcycle.backend.commerce.billing.infrastructure.toss.TossBillingAdapter;
import com.pawcycle.backend.commerce.delivery.application.DeliveryService;
import com.pawcycle.backend.commerce.inventory.application.InventoryService;
import com.pawcycle.backend.commerce.membership.application.MembershipEvaluationService;
import com.pawcycle.backend.commerce.notification.application.NotificationService;
import com.pawcycle.backend.commerce.payment.application.PaymentReconciliationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawcycle.backend.subscription.persistence.SubscriptionBillingPersistence;
import com.pawcycle.backend.subscription.persistence.SubscriptionBillingPersistence.BillingCandidate;
import com.pawcycle.backend.subscription.persistence.SubscriptionBillingPersistence.ProcessingPayment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(OutputCaptureExtension.class)
class SubscriptionBillingProcessorTests {
  @Test
  void successfulSubscriptionBillingReevaluatesMembership() {
    SubscriptionBillingPersistence jdbc = mock(SubscriptionBillingPersistence.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    org.springframework.transaction.TransactionStatus status =
        mock(org.springframework.transaction.TransactionStatus.class);
    MembershipEvaluationService membership = mock(MembershipEvaluationService.class);
    when(manager.getTransaction(any(org.springframework.transaction.TransactionDefinition.class)))
        .thenReturn(status);
    org.mockito.Mockito.doNothing().when(manager).commit(status);
    when(jdbc.lockProcessingPayment(8L)).thenReturn(new ProcessingPayment(8L, 9L, 10L, 11L));
    when(jdbc.findOrderedItems(9L)).thenReturn(List.of());
    SubscriptionBillingProcessor processor =
        new SubscriptionBillingProcessor(
            jdbc,
            manager,
            mock(TossBillingAdapter.class),
            mock(SubscriptionBillingService.class),
            mock(PaymentReconciliationService.class),
            mock(DeliveryService.class),
            mock(NotificationService.class),
            membership,
            mock(InventoryService.class),
            java.time.Clock.systemUTC(),
            new ExternalCallFailures(new SimpleMeterRegistry()));

    processor.completeSuccess(8L, "DONE");

    verify(membership).evaluate(10L);
  }

  @Test
  void processingBillingIsReconciledWithoutChargingAgain() {
    SubscriptionBillingPersistence jdbc = mock(SubscriptionBillingPersistence.class);
    TossBillingAdapter provider = mock(TossBillingAdapter.class);
    SubscriptionBillingService retries = mock(SubscriptionBillingService.class);
    PaymentReconciliationService reconciliation = mock(PaymentReconciliationService.class);
    when(provider.isConfigured()).thenReturn(true);
    when(jdbc.findCandidates()).thenReturn(List.of(new BillingCandidate(42L, "PROCESSING")));

    SubscriptionBillingProcessor processor =
        new SubscriptionBillingProcessor(
            jdbc,
            mock(PlatformTransactionManager.class),
            provider,
            retries,
            reconciliation,
            mock(DeliveryService.class),
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(InventoryService.class),
            java.time.Clock.systemUTC(),
            new ExternalCallFailures(new SimpleMeterRegistry()));

    assertThat(processor.processReadyPayments()).isEqualTo(1);
    verify(reconciliation).reconcile(42L);
    verify(provider, never()).charge(anyString(), anyString(), any());
  }

  @Test
  void lastFailedAttemptIsRecordedEvenWhenNoFurtherAttemptMayBePrepared(CapturedOutput output) {
    SubscriptionBillingPersistence jdbc = mock(SubscriptionBillingPersistence.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any(org.springframework.transaction.TransactionDefinition.class)))
        .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
    TossBillingAdapter provider = mock(TossBillingAdapter.class);
    SubscriptionBillingService retries = mock(SubscriptionBillingService.class);
    when(provider.isConfigured()).thenReturn(true);
    when(jdbc.lockWork(77L)).thenReturn(new SubscriptionBillingPersistence.BillingWork(
        77L, 9L, "billing-order-77", java.math.BigDecimal.TEN, "READY", "billing-key", 5L, 6L, 10L));
    when(provider.charge("billing-key", "billing-order-77", java.math.BigDecimal.TEN))
        .thenReturn(new TossBillingAdapter.ChargeResult("FAILED", "REJECT_CARD_COMPANY"));
    org.mockito.Mockito.doThrow(new com.pawcycle.backend.commerce.common.error.CommerceException(
            409, "PAYMENT_RETRY_NOT_ALLOWED", "no more attempts"))
        .when(retries).prepareNextAttempt(77L);
    SubscriptionBillingProcessor processor =
        new SubscriptionBillingProcessor(
            jdbc,
            manager,
            provider,
            retries,
            mock(PaymentReconciliationService.class),
            mock(DeliveryService.class),
            mock(NotificationService.class),
            mock(MembershipEvaluationService.class),
            mock(InventoryService.class),
            java.time.Clock.systemUTC(),
            new ExternalCallFailures(new SimpleMeterRegistry()));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process(77L))
        .isInstanceOf(com.pawcycle.backend.commerce.common.error.CommerceException.class);

    verify(retries).recordExplicitFailure(77L, "TOSS_REJECTED");
    assertThat(output.getOut())
        .contains("event=billing.charge.finished paymentId=77 providerStatus=REJECT_CARD_COMPANY outcome=FAILED");
  }
}
