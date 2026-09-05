package com.pawcycle.backend.commerce;

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
            java.time.Clock.systemUTC());

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
            java.time.Clock.systemUTC());

    assertThat(processor.processReadyPayments()).isEqualTo(1);
    verify(reconciliation).reconcile(42L);
    verify(provider, never()).charge(anyString(), anyString(), any());
  }
}
